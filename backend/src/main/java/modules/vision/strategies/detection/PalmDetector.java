package modules.vision.strategies.detection;

import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Erkennt Handflächen (Palm) im Bild - Ersatz für den bisherigen YOLO-basierten
 * {@code HandDetector} (jetzt entfernt). Nutzt Googles offizielles MediaPipe-Palm-Detection-
 * Modell (über den OpenCV Zoo als ONNX bereitgestellt), das auf deutlich diverseren
 * Trainingsdaten (Hauttöne, Beleuchtung, Hintergründe inkl. Outdoor) trainiert wurde als
 * das vorherige Community-YOLO-Modell - sollte robuster gegen Fehlerkennungen bei bunten
 * Hintergründen und verpasste Erkennungen bei offener Hand sein.
 * <p>
 * Pre-/Postprocessing ist 1:1 aus der offiziellen Referenz-Implementierung
 * (opencv/palm_detection_mediapipe, demo.cpp) übernommen, um die exakte Anker-Logik und
 * Koordinaten-Rückrechnung nicht neu erraten zu müssen.
 */
public class PalmDetector {

    private static final int INPUT_SIZE = 192;
    private static final int NUM_GRID_CELLS = 24;
    private static final int ANCHORS_PER_CELL = 2;
    private static final int BOX_COLUMNS = 18;
    private static final int NUM_PALM_LANDMARKS = 7;

    private final Net net;
    private final List<String> outBlobNames;
    private final List<Point> anchors;
    private final float scoreThreshold;
    private final float nmsThreshold;

    public PalmDetector() {
        this(0.5f, 0.3f);
    }

    public PalmDetector(float scoreThreshold, float nmsThreshold) {
        this.scoreThreshold = scoreThreshold;
        this.nmsThreshold = nmsThreshold;

        File modelFile = new File("backend/src/main/resources/models/palm_detection_mediapipe_2023feb.onnx");
        if (!modelFile.exists()) {
            modelFile = new File("src/main/resources/models/palm_detection_mediapipe_2023feb.onnx");
        }
        if (!modelFile.exists()) {
            throw new RuntimeException(
                    "[FEHLER] Palm-Detection-Modell nicht gefunden unter: " + modelFile.getAbsolutePath() +
                            "\nBitte palm_detection_mediapipe_2023feb.onnx von " +
                            "https://huggingface.co/opencv/opencv_zoo/resolve/main/models/palm_detection_mediapipe/palm_detection_mediapipe_2023feb.onnx " +
                            "herunterladen und unter backend/src/main/resources/models/ ablegen."
            );
        }

        this.net = Dnn.readNetFromONNX(modelFile.getAbsolutePath());
        this.net.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
        this.net.setPreferableTarget(Dnn.DNN_TARGET_CPU);
        this.outBlobNames = net.getUnconnectedOutLayersNames();

        this.anchors = generateAnchors();
    }

    /**
     * Drop-in-Ersatz für die alte HandDetector.detectHand()-Methode: liefert nur die
     * Bounding Box der besten Erkennung (oder null). Für Stellen, die bewusst nur EINE
     * Hand pro Frame verarbeiten sollen (z.B. DataCollectorApp).
     */
    public Rect detectHand(Mat frame) {
        PalmDetection best = detectPalm(frame);
        return best != null ? best.box : null;
    }

    /**
     * Wie {@link #detectHand(Mat)}, liefert zusätzlich die 7 Palm-Keypoints. Gibt die
     * Erkennung mit der höchsten Konfidenz zurück, oder null falls keine Hand über der
     * Schwelle gefunden wurde.
     */
    public PalmDetection detectPalm(Mat frame) {
        List<PalmDetection> all = detectAllPalms(frame);
        if (all.isEmpty()) {
            return null;
        }
        PalmDetection best = all.get(0);
        for (PalmDetection d : all) {
            if (d.score() > best.score()) {
                best = d;
            }
        }
        return best;
    }

    /**
     * Liefert ALLE nach Non-Max-Suppression übrig gebliebenen Hand-Erkennungen im Frame
     * (nicht nur die beste) - für die Mehrhand-Live-Auswertung.
     */
    public List<PalmDetection> detectAllPalms(Mat frame) {
        if (frame == null || frame.empty()) {
            return new ArrayList<>();
        }

        Mat resized = null;
        Mat padded = null;
        Mat rgb = null;
        Mat floatImg = null;
        Mat blob = null;
        List<Mat> rawOutputs = new ArrayList<>();

        try {
            float ratio = Math.min((float) INPUT_SIZE / frame.cols(), (float) INPUT_SIZE / frame.rows());
            int resizedW = Math.round(frame.cols() * ratio);
            int resizedH = Math.round(frame.rows() * ratio);

            resized = new Mat();
            Imgproc.resize(frame, resized, new Size(resizedW, resizedH));

            int padW = INPUT_SIZE - resizedW;
            int padH = INPUT_SIZE - resizedH;
            int padLeft = padW / 2;
            int padTop = padH / 2;

            padded = new Mat();
            Core.copyMakeBorder(resized, padded, padTop, padH - padTop, padLeft, padW - padLeft,
                    Core.BORDER_CONSTANT, new Scalar(0, 0, 0));
            float padBiasX = padLeft / ratio;
            float padBiasY = padTop / ratio;
            rgb = new Mat();
            Imgproc.cvtColor(padded, rgb, Imgproc.COLOR_BGR2RGB);

            floatImg = new Mat();
            rgb.convertTo(floatImg, CvType.CV_32FC3, 1.0 / 255.0);
            float[] hwcData = new float[INPUT_SIZE * INPUT_SIZE * 3];
            floatImg.get(0, 0, hwcData);

            blob = new Mat(new int[]{1, INPUT_SIZE, INPUT_SIZE, 3}, CvType.CV_32F);
            blob.put(new int[]{0, 0, 0, 0}, hwcData);
            net.setInput(blob);
            net.forward(rawOutputs, outBlobNames);
            float[] boxesFlat = null;
            float[] scoresFlat = null;

            for (Mat rawOut : rawOutputs) {
                if (rawOut == null || rawOut.empty()) continue;
                Mat flat = rawOut.reshape(1, 1);
                int total = (int) flat.total();
                float[] values = new float[total];
                flat.get(0, 0, values);

                if (boxesFlat == null && scoresFlat == null) {
                    boxesFlat = values;
                } else {
                    scoresFlat = values;
                }
            }
            if (boxesFlat != null && scoresFlat != null && boxesFlat.length < scoresFlat.length) {
                float[] tmp = boxesFlat;
                boxesFlat = scoresFlat;
                scoresFlat = tmp;
            }

            if (boxesFlat == null || scoresFlat == null) {
                System.err.println("[PALM FEHLER] Unerwartetes Output-Format vom Modell.");
                return new ArrayList<>();
            }

            int numAnchorsFound = scoresFlat.length;
            float scale = Math.max(frame.cols(), frame.rows());

            List<Rect> candidateBoxes = new ArrayList<>();
            List<Float> candidateScores = new ArrayList<>();
            List<Point[]> candidateLandmarks = new ArrayList<>();

            float maxScoreOverall = 0f;

            for (int i = 0; i < numAnchorsFound && i < anchors.size(); i++) {
                float rawScore = scoresFlat[i];
                float score = (float) (1.0 / (1.0 + Math.exp(-rawScore)));
                maxScoreOverall = Math.max(maxScoreOverall, score);

                if (score <= scoreThreshold) continue;

                int base = i * BOX_COLUMNS;
                Point anchor = anchors.get(i);

                float cxyDeltaX = boxesFlat[base] / INPUT_SIZE;
                float cxyDeltaY = boxesFlat[base + 1] / INPUT_SIZE;
                float whDeltaX = boxesFlat[base + 2] / INPUT_SIZE;
                float whDeltaY = boxesFlat[base + 3] / INPUT_SIZE;

                float x1 = (float) ((cxyDeltaX - whDeltaX / 2 + anchor.x) * scale - padBiasX);
                float y1 = (float) ((cxyDeltaY - whDeltaY / 2 + anchor.y) * scale - padBiasY);
                float x2 = (float) ((cxyDeltaX + whDeltaX / 2 + anchor.x) * scale - padBiasX);
                float y2 = (float) ((cxyDeltaY + whDeltaY / 2 + anchor.y) * scale - padBiasY);

                candidateBoxes.add(new Rect((int) x1, (int) y1, (int) (x2 - x1), (int) (y2 - y1)));
                candidateScores.add(score);

                Point[] landmarks = new Point[NUM_PALM_LANDMARKS];
                for (int j = 0; j < NUM_PALM_LANDMARKS; j++) {
                    float dx = boxesFlat[base + 4 + j * 2] / INPUT_SIZE;
                    float dy = boxesFlat[base + 4 + j * 2 + 1] / INPUT_SIZE;
                    dx += anchor.x;
                    dy += anchor.y;
                    landmarks[j] = new Point(dx * scale - padBiasX, dy * scale - padBiasY);
                }
                candidateLandmarks.add(landmarks);
            }

            System.out.println("DEBUG: höchste Palm-Konfidenz in diesem Frame (ungefiltert) = " + maxScoreOverall);

            if (candidateBoxes.isEmpty()) {
                return new ArrayList<>();
            }
            Rect2d[] boxesForNms = new Rect2d[candidateBoxes.size()];
            for (int i = 0; i < candidateBoxes.size(); i++) {
                Rect r = candidateBoxes.get(i);
                boxesForNms[i] = new Rect2d(r.x, r.y, r.width, r.height);
            }
            MatOfRect2d boxesMat = new MatOfRect2d(boxesForNms);
            MatOfFloat scoresMat = new MatOfFloat(toFloatArray(candidateScores));
            MatOfInt indicesMat = new MatOfInt();
            Dnn.NMSBoxes(boxesMat, scoresMat, scoreThreshold, nmsThreshold, indicesMat);

            int[] keptIndices = indicesMat.toArray();
            if (keptIndices.length == 0) {
                return new ArrayList<>();
            }

            List<PalmDetection> results = new ArrayList<>(keptIndices.length);
            for (int idx : keptIndices) {
                Rect box = restrictToFrame(candidateBoxes.get(idx), frame.cols(), frame.rows());
                results.add(new PalmDetection(box, candidateLandmarks.get(idx), candidateScores.get(idx)));
            }
            results.sort((a, b) -> Float.compare(b.score(), a.score()));
            return results;

        } catch (Exception e) {
            System.err.println("[PALM FEHLER ABGEFANGEN]: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            if (resized != null) resized.release();
            if (padded != null) padded.release();
            if (rgb != null) rgb.release();
            if (floatImg != null) floatImg.release();
            if (blob != null) blob.release();
            for (Mat mat : rawOutputs) {
                if (mat != null) mat.release();
            }
        }
    }

    /**
     * Erzeugt die 1152 SSD-Anker (24x24-Raster, 2 Anker pro Zelle, normierte 0..1-Koordinaten)
     * programmatisch, statt sie hartkodiert zu übernehmen - das Muster ist eindeutig aus der
     * Referenz-Implementierung ablesbar.
     */
    private static List<Point> generateAnchors() {
        List<Point> result = new ArrayList<>(NUM_GRID_CELLS * NUM_GRID_CELLS * ANCHORS_PER_CELL);
        for (int row = 0; row < NUM_GRID_CELLS; row++) {
            double y = (row + 0.5) / NUM_GRID_CELLS;
            for (int col = 0; col < NUM_GRID_CELLS; col++) {
                double x = (col + 0.5) / NUM_GRID_CELLS;
                for (int a = 0; a < ANCHORS_PER_CELL; a++) {
                    result.add(new Point(x, y));
                }
            }
        }
        return result;
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private static Rect restrictToFrame(Rect rect, int maxWidth, int maxHeight) {
        int x = Math.max(0, rect.x);
        int y = Math.max(0, rect.y);
        int width = Math.min(maxWidth - x, rect.width);
        int height = Math.min(maxHeight - y, rect.height);
        return new Rect(x, y, Math.max(0, width), Math.max(0, height));
    }

    /**
     * @param box       Bounding Box der Handfläche in Original-Frame-Koordinaten
     * @param landmarks 7 Palm-Keypoints in Original-Frame-Koordinaten (für künftiges
     *                  Rotations-Alignment nutzbar)
     * @param score     Konfidenz der Erkennung (0..1)
     */
    public record PalmDetection(Rect box, Point[] landmarks, float score) {}
}
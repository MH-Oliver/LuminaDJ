package modules.vision.strategies.detection;

import modules.vision.structures.HandLandmarks;
import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Extrahiert 21 Hand-Keypoints (MediaPipe-Hand-Schema) aus einem Bildausschnitt,
 * der bereits eine Hand enthält.
 * <p>
 * Nutzt das offizielle, Apache-2.0-lizenzierte ONNX-Modell aus dem OpenCV Zoo
 * (opencv/handpose_estimation_mediapipe, "2023feb"-Variante).
 * <p>
 * Vereinfachung ggü. der originalen MediaPipe-Pipeline: Dort wird die Hand vor der
 * Landmark-Erkennung anhand eines Palm-Detektors rotiert ausgerichtet. Da wir bereits
 * einen YOLO-Hand-Detector nutzen, verzichten wir auf diesen Rotationsschritt und
 * füttern stattdessen einen vergrößerten, quadratischen Crop direkt hinein. Die
 * Rotationsinvarianz wird stattdessen später bei der Normalisierung der Landmarks
 * (für die Gesten-Klassifikation) hergestellt.
 */
public class HandLandmarkExtractor {
    private static final int INPUT_SIZE = 256;
    private static final int NUM_LANDMARKS = 21;
    private static final double BBOX_MARGIN_FACTOR = 2.6;

    private final Net landmarkNet;
    private final List<String> outBlobNames;


    public HandLandmarkExtractor() {


        File modelFile = new File("models/handpose_estimation_mediapipe_2023feb.onnx");
        if (!modelFile.exists()) {
            modelFile = new File("backend/src/main/resources/models/handpose_estimation_mediapipe_2023feb.onnx");
        }
        if (!modelFile.exists()) {
            modelFile = new File("src/main/resources/models/handpose_estimation_mediapipe_2023feb.onnx");
        }

        this.landmarkNet = Dnn.readNetFromONNX(modelFile.getAbsolutePath());
        this.landmarkNet.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
        this.landmarkNet.setPreferableTarget(Dnn.DNN_TARGET_CPU);

        this.outBlobNames = landmarkNet.getUnconnectedOutLayersNames();
    }

    /**
     * Extrahiert die 21 Hand-Keypoints aus dem übergebenen Frame, ausgehend von einer
     * bereits erkannten Hand-Bounding-Box
     *
     * @param frame    Das Original-Kamerabild (BGR, wie von OpenCV üblich)
     * @param handBbox Grobe Bounding Box der Hand im Original-Frame
     * @return Landmarks in Original-Frame-Koordinaten, oder null bei zu geringer Konfidenz/Fehler
     */
    public HandLandmarks extractLandmarks(Mat frame, Rect handBbox) {
        return extractLandmarksInternal(frame, handBbox, null, BBOX_MARGIN_FACTOR);
    }

    /**
     * Wie {@link #extractLandmarks(Mat, Rect)}, speichert zusätzlich (falls debugCropOutputFile
     * nicht null ist) eine Visualisierung DIREKT auf dem 256x256-Modell-Input, OHNE
     * Rückrechnung auf Frame-Koordinaten. Damit lässt sich unterscheiden, ob ein sichtbarer
     * Versatz vom Modell selbst kommt (dann ist er hier schon sichtbar) oder erst durch die
     * Rückrechnung auf den Original-Frame entsteht (dann ist dieses Bild sauber, aber das
     * Overlay auf dem Original-Frame nicht).
     */
    public HandLandmarks extractLandmarks(Mat frame, Rect handBbox, File debugCropOutputFile) {
        return extractLandmarksInternal(frame, handBbox, debugCropOutputFile, BBOX_MARGIN_FACTOR);
    }

    /**
     * Für bereits zugeschnittene Hand-Bilder (z.B. die von DataCollectorApp gespeicherten
     * Trainings-PNGs, die schon nur die Hand zeigen). Hier braucht es keinen zusätzlichen
     * Vergrößerungs-Faktor mehr (marginFactor=1.0) - das Bild wird nur quadratisch gemacht
     * (falls nötig) und direkt an das Modell gegeben.
     */
    public HandLandmarks extractLandmarksFromPreCroppedImage(Mat alreadyCroppedHand) {
        if (alreadyCroppedHand == null || alreadyCroppedHand.empty()) {
            return null;
        }
        Rect fullImageBbox = new Rect(0, 0, alreadyCroppedHand.cols(), alreadyCroppedHand.rows());
        return extractLandmarksInternal(alreadyCroppedHand, fullImageBbox, null, 1.0);
    }

    private HandLandmarks extractLandmarksInternal(Mat frame, Rect handBbox, File debugCropOutputFile, double marginFactor) {
        if (frame == null || frame.empty() || handBbox == null) {
            return null;
        }

        Mat squareCrop = null;
        Mat rgbCrop = null;
        Mat resizedCrop = null;
        Mat floatCrop = null;
        Mat blob = null;
        List<Mat> rawOutputs = new ArrayList<>();

        try {
            SquareCropInfo cropInfo = squarifyAndCrop(frame, handBbox, marginFactor);
            squareCrop = cropInfo.crop;
            rgbCrop = new Mat();
            Imgproc.cvtColor(squareCrop, rgbCrop, Imgproc.COLOR_BGR2RGB);
            resizedCrop = new Mat();
            Imgproc.resize(rgbCrop, resizedCrop, new Size(INPUT_SIZE, INPUT_SIZE), 0, 0, Imgproc.INTER_LINEAR);
            floatCrop = new Mat();
            resizedCrop.convertTo(floatCrop, CvType.CV_32FC3, 1.0 / 255.0);
            float[] hwcData = new float[INPUT_SIZE * INPUT_SIZE * 3];
            floatCrop.get(0, 0, hwcData);

            blob = new Mat(new int[]{1, INPUT_SIZE, INPUT_SIZE, 3}, CvType.CV_32F);
            blob.put(new int[]{0, 0, 0, 0}, hwcData);
            landmarkNet.setInput(blob);
            landmarkNet.forward(rawOutputs, outBlobNames);
            float[] landmarkData = null;
            float confidence = 0f;

            for (Mat rawOut : rawOutputs) {
                if (rawOut == null || rawOut.empty()) continue;

                Mat flat = rawOut.reshape(1, 1);
                int total = (int) flat.total();
                float[] values = new float[total];
                flat.get(0, 0, values);

                if (total == NUM_LANDMARKS * 3) {
                    float maxAbs = 0f;
                    for (float v : values) maxAbs = Math.max(maxAbs, Math.abs(v));
                    if (maxAbs > 5f) {
                        landmarkData = values;
                    }
                } else if (total == 1) {
                    confidence = values[0];
                }
            }

            if (landmarkData == null) {
                System.err.println("[LANDMARK FEHLER] Unerwartetes Output-Format vom Modell.");
                return null;
            }
            if (debugCropOutputFile != null) {
                saveCropDebugVisualization(resizedCrop, landmarkData, debugCropOutputFile);
            }

            System.out.println("DEBUG: Landmark-Konfidenz = " + confidence);
            Point[] points = new Point[NUM_LANDMARKS];
            float[] zValues = new float[NUM_LANDMARKS];

            for (int i = 0; i < NUM_LANDMARKS; i++) {
                float xModel = landmarkData[i * 3];
                float yModel = landmarkData[i * 3 + 1];
                float zModel = landmarkData[i * 3 + 2];

                double xNorm = xModel / INPUT_SIZE;
                double yNorm = yModel / INPUT_SIZE;

                double xOrig = cropInfo.offsetX + xNorm * cropInfo.size;
                double yOrig = cropInfo.offsetY + yNorm * cropInfo.size;

                points[i] = new Point(xOrig, yOrig);
                zValues[i] = (float) (zModel / INPUT_SIZE);
            }

            return new HandLandmarks(points, zValues, confidence);

        } catch (Exception e) {
            System.err.println("[LANDMARK FEHLER ABGEFANGEN]: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (squareCrop != null) squareCrop.release();
            if (rgbCrop != null) rgbCrop.release();
            if (resizedCrop != null) resizedCrop.release();
            if (floatCrop != null) floatCrop.release();
            if (blob != null) blob.release();
            for (Mat mat : rawOutputs) {
                if (mat != null) mat.release();
            }
        }
    }
    private static final int[][] HAND_CONNECTIONS = {
            {0, 1}, {1, 2}, {2, 3}, {3, 4},
            {0, 5}, {5, 6}, {6, 7}, {7, 8},
            {0, 9}, {9, 10}, {10, 11}, {11, 12},
            {0, 13}, {13, 14}, {14, 15}, {15, 16},
            {0, 17}, {17, 18}, {18, 19}, {19, 20},
            {5, 9}, {9, 13}, {13, 17}
    };

    /**
     * Zeichnet die rohen Landmark-Koordinaten (0..256, wie direkt vom Modell geliefert) auf
     * den tatsächlichen 256x256-Modell-Input, ohne jede Rückrechnung auf Frame-Koordinaten.
     * Reiner Diagnose-Zweck: zeigt die "Wahrheit aus Sicht des Modells".
     */
    private static void saveCropDebugVisualization(Mat resizedCropRgb, float[] landmarkData, File outputFile) {
        Mat bgr = new Mat();
        try {
            Imgproc.cvtColor(resizedCropRgb, bgr, Imgproc.COLOR_RGB2BGR);

            Point[] rawPoints = new Point[NUM_LANDMARKS];
            for (int i = 0; i < NUM_LANDMARKS; i++) {
                rawPoints[i] = new Point(landmarkData[i * 3], landmarkData[i * 3 + 1]);
            }

            for (int[] connection : HAND_CONNECTIONS) {
                Imgproc.line(bgr, rawPoints[connection[0]], rawPoints[connection[1]],
                        new Scalar(0, 255, 0), 1);
            }
            for (Point p : rawPoints) {
                Imgproc.circle(bgr, p, 3, new Scalar(0, 0, 255), -1);
            }

            Imgcodecs.imwrite(outputFile.getAbsolutePath(), bgr);
        } finally {
            bgr.release();
        }
    }

    /**
     * Zeichnet Landmarks + Skelett-Verbindungen DIREKT auf die übergebene Mat (kein Klon) -
     * nützlich, um mehrere erkannte Hände nacheinander auf dasselbe Anzeige-Bild zu zeichnen.
     */
    public static void drawLandmarksOnto(Mat target, HandLandmarks landmarks) {
        if (target == null || landmarks == null) {
            return;
        }

        Point[] points = landmarks.points();

        for (int[] connection : HAND_CONNECTIONS) {
            Imgproc.line(target, points[connection[0]], points[connection[1]],
                    new Scalar(0, 255, 0), 2);
        }

        for (int i = 0; i < points.length; i++) {
            Imgproc.circle(target, points[i], 4, new Scalar(0, 0, 255), -1);
            if (i == 0 || i == 4 || i == 8) {
                Imgproc.putText(target, String.valueOf(i), points[i],
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 255, 0), 1);
            }
        }
    }

    /**
     * Zeichnet Landmarks + Skelett-Verbindungen auf eine KOPIE des Frames und gibt diese
     * zurück (Original-Frame bleibt unverändert). Aufrufer ist für das Freigeben der
     * zurückgegebenen Mat verantwortlich.
     */
    public static Mat drawLandmarksOverlay(Mat frame, HandLandmarks landmarks) {
        Mat vis = frame.clone();
        drawLandmarksOnto(vis, landmarks);
        return vis;
    }

    public static void saveDebugVisualization(Mat frame, HandLandmarks landmarks, File outputFile) {
        if (frame == null || frame.empty() || landmarks == null) {
            return;
        }

        Mat vis = drawLandmarksOverlay(frame, landmarks);
        try {
            Imgcodecs.imwrite(outputFile.getAbsolutePath(), vis);
        } finally {
            vis.release();
        }
    }

    private SquareCropInfo squarifyAndCrop(Mat frame, Rect bbox, double marginFactor) {
        double centerX = bbox.x + bbox.width / 2.0;
        double centerY = bbox.y + bbox.height / 2.0;

        int squareSize = (int) (Math.max(bbox.width, bbox.height) * marginFactor);
        int offsetX = (int) (centerX - squareSize / 2.0);
        int offsetY = (int) (centerY - squareSize / 2.0);
        int padLeft = Math.max(0, -offsetX);
        int padTop = Math.max(0, -offsetY);
        int padRight = Math.max(0, (offsetX + squareSize) - frame.cols());
        int padBottom = Math.max(0, (offsetY + squareSize) - frame.rows());
        int validX = Math.max(0, offsetX);
        int validY = Math.max(0, offsetY);
        int validWidth = squareSize - padLeft - padRight;
        int validHeight = squareSize - padTop - padBottom;
        validWidth = Math.min(validWidth, frame.cols() - validX);
        validHeight = Math.min(validHeight, frame.rows() - validY);

        Mat validRegion = new Mat(frame, new Rect(validX, validY, validWidth, validHeight));

        Mat squareCrop = new Mat();
        Core.copyMakeBorder(validRegion, squareCrop, padTop, squareSize - validHeight - padTop,
                padLeft, squareSize - validWidth - padLeft, Core.BORDER_CONSTANT, new Scalar(0, 0, 0));

        return new SquareCropInfo(squareCrop, offsetX, offsetY, squareSize);
    }

    private static class SquareCropInfo {
        final Mat crop;
        final int offsetX;
        final int offsetY;
        final int size;

        SquareCropInfo(Mat crop, int offsetX, int offsetY, int size) {
            this.crop = crop;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.size = size;
        }
    }
}
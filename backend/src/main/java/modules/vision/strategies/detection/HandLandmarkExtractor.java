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
 * der bereits eine Hand enthält (z.B. der Crop aus {@link HandDetector}).
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

    // Modell erwartet exakt 256x256 als Eingabegröße (siehe mp_handpose.py, self.input_size).
    private static final int INPUT_SIZE = 256;
    private static final int NUM_LANDMARKS = 21;

    // WICHTIG: Der alte Wert 1.3 war für die frühere YOLO-Hand-Box kalibriert, die schon
    // einen Großteil der Finger mit abdeckte. Der neue PalmDetector liefert dagegen bewusst
    // NUR die Handfläche (ohne Finger) - die frühere Erkenntnis "höherer Margin macht es
    // schlechter" bezog sich auf einen strukturell anderen Box-Typ und gilt hier nicht mehr.
    // 2.6 entspricht dem offiziellen MediaPipe-Vergrößerungsfaktor für genau diesen Schritt
    // (Palm-Box -> Hand-Crop).
    private static final double BBOX_MARGIN_FACTOR = 2.6;

    private final Net landmarkNet;
    private final List<String> outBlobNames;
    private final float confidenceThreshold;

    public HandLandmarkExtractor() {
        this(0.8f);
    }

    public HandLandmarkExtractor(float confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;

        File modelFile = new File("backend/src/main/resources/models/handpose_estimation_mediapipe_2023feb.onnx");
        if (!modelFile.exists()) {
            modelFile = new File("src/main/resources/models/handpose_estimation_mediapipe_2023feb.onnx");
        }

        if (!modelFile.exists()) {
            throw new RuntimeException(
                    "[FEHLER] Landmark-Modell nicht gefunden unter: " + modelFile.getAbsolutePath() +
                            "\nBitte handpose_estimation_mediapipe_2023feb.onnx von " +
                            "https://huggingface.co/opencv/handpose_estimation_mediapipe herunterladen " +
                            "und unter backend/src/main/resources/models/ ablegen."
            );
        }

        this.landmarkNet = Dnn.readNetFromONNX(modelFile.getAbsolutePath());
        this.landmarkNet.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
        this.landmarkNet.setPreferableTarget(Dnn.DNN_TARGET_CPU);

        this.outBlobNames = landmarkNet.getUnconnectedOutLayersNames();
    }

    /**
     * Extrahiert die 21 Hand-Keypoints aus dem übergebenen Frame, ausgehend von einer
     * bereits erkannten Hand-Bounding-Box (z.B. aus {@link HandDetector#detectHand(Mat)}).
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
            // 1. Bounding Box vergrößern, quadratisch machen und aus dem Frame ausschneiden
            //    (mit schwarzem Padding, falls der vergrößerte Bereich über den Frame-Rand hinausragt).
            SquareCropInfo cropInfo = squarifyAndCrop(frame, handBbox, marginFactor);
            squareCrop = cropInfo.crop;

            // 2. BGR -> RGB, denn das Modell wurde auf RGB-Bildern trainiert (siehe mp_handpose.py).
            rgbCrop = new Mat();
            Imgproc.cvtColor(squareCrop, rgbCrop, Imgproc.COLOR_BGR2RGB);

            // 3. Auf 256x256 skalieren. INTER_LINEAR statt INTER_AREA, da der Crop meist KLEINER
            //    als 256x256 ist und wir also vergrößern (INTER_AREA ist für Verkleinerung gedacht).
            resizedCrop = new Mat();
            Imgproc.resize(rgbCrop, resizedCrop, new Size(INPUT_SIZE, INPUT_SIZE), 0, 0, Imgproc.INTER_LINEAR);

            // 4. Auf float32 im Bereich [0,1] normalisieren.
            floatCrop = new Mat();
            resizedCrop.convertTo(floatCrop, CvType.CV_32FC3, 1.0 / 255.0);

            // 5. Manuell einen NHWC-Blob (1,256,256,3) bauen. WICHTIG: Dnn.blobFromImage() würde
            //    standardmäßig NCHW (1,3,256,256) erzeugen, das dieses Modell NICHT erwartet.
            //    Eine CV_32FC3-Mat speichert Pixel bereits interleaved als H x W x 3 (HWC) im Speicher,
            //    das entspricht exakt NHWC mit N=1 - wir müssen die Daten nur "umdeklarieren", nicht umsortieren.
            float[] hwcData = new float[INPUT_SIZE * INPUT_SIZE * 3];
            floatCrop.get(0, 0, hwcData);

            blob = new Mat(new int[]{1, INPUT_SIZE, INPUT_SIZE, 3}, CvType.CV_32F);
            blob.put(new int[]{0, 0, 0, 0}, hwcData);

            // 6. Inferenz
            landmarkNet.setInput(blob);
            landmarkNet.forward(rawOutputs, outBlobNames);

            // 7. Dieses Modell hat 4 Outputs (nicht 2, wie in älteren Doku-Versionen beschrieben):
            //    - ein 63-Werte-Output mit Bild-Pixel-Koordinaten (0..256) -> das wollen wir
            //    - ein 63-Werte-Output mit "World Landmarks" (metrische 3D-Koordinaten, sehr kleine
            //      Werte um die reale Handgröße in Metern) -> nicht das, was wir brauchen
            //    - zwei 1-Werte-Outputs (u.a. Konfidenz)
            //    Wir unterscheiden die zwei 63er-Outputs NICHT über die Reihenfolge (die könnte sich
            //    zwischen Modellversionen ändern), sondern über den Wertebereich: Bild-Koordinaten
            //    liegen im Bereich 0..256, World-Landmarks liegen im Bereich von wenigen Zentimetern.
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

                    // Bild-Koordinaten-Output hat Werte bis ~256, World-Landmarks bleiben unter ~1.
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

            // Debug: Landmarks direkt auf dem Modell-Input zeichnen, OHNE jede Rückrechnung -
            // zeigt, ob das Modell selbst schon daneben liegt oder ob der Fehler erst später
            // (beim Zurückrechnen auf den Original-Frame) entsteht. Wird unabhängig von der
            // Konfidenz gespeichert, um auch knapp-unter-Schwelle-Fälle einsehen zu können.
            if (debugCropOutputFile != null) {
                saveCropDebugVisualization(resizedCrop, landmarkData, debugCropOutputFile);
            }

            if (confidence < confidenceThreshold) {
                return null;
            }

            // 8. Landmark-Koordinaten (0..256 im Crop) zurück auf Original-Frame-Pixelkoordinaten
            //    umrechnen, analog zur Letterboxing-Rückrechnung in HandDetector.
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
                zValues[i] = (float) (zModel / INPUT_SIZE); // grob skalierte relative Tiefe
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

    // Standard-MediaPipe-Hand-Skelett-Verbindungen (welche Landmark-Indizes durch eine Linie
    // verbunden werden), für die Debug-Visualisierung.
    private static final int[][] HAND_CONNECTIONS = {
            {0, 1}, {1, 2}, {2, 3}, {3, 4},         // Daumen
            {0, 5}, {5, 6}, {6, 7}, {7, 8},         // Zeigefinger
            {0, 9}, {9, 10}, {10, 11}, {11, 12},    // Mittelfinger
            {0, 13}, {13, 14}, {14, 15}, {15, 16},  // Ringfinger
            {0, 17}, {17, 18}, {18, 19}, {19, 20},  // Kleiner Finger
            {5, 9}, {9, 13}, {13, 17}               // Handfläche quer
    };

    /**
     * Zeichnet die rohen Landmark-Koordinaten (0..256, wie direkt vom Modell geliefert) auf
     * den tatsächlichen 256x256-Modell-Input, ohne jede Rückrechnung auf Frame-Koordinaten.
     * Reiner Diagnose-Zweck: zeigt die "Wahrheit aus Sicht des Modells".
     */
    private static void saveCropDebugVisualization(Mat resizedCropRgb, float[] landmarkData, File outputFile) {
        Mat bgr = new Mat();
        try {
            // resizedCropRgb ist RGB (wir haben vorher extra dorthin konvertiert) - für die
            // Anzeige/Speicherung als normales Bild zurück zu BGR konvertieren.
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

    public static void saveDebugVisualization(Mat frame, HandLandmarks landmarks, File outputFile) {
        if (frame == null || frame.empty() || landmarks == null) {
            return;
        }

        Mat vis = frame.clone();
        try {
            Point[] points = landmarks.points();

            for (int[] connection : HAND_CONNECTIONS) {
                Imgproc.line(vis, points[connection[0]], points[connection[1]],
                        new Scalar(0, 255, 0), 2);
            }

            for (int i = 0; i < points.length; i++) {
                Imgproc.circle(vis, points[i], 4, new Scalar(0, 0, 255), -1);
                // Wrist (0), Daumen-Spitze (4) und Zeigefinger-Spitze (8) zur Orientierung beschriften.
                if (i == 0 || i == 4 || i == 8) {
                    Imgproc.putText(vis, String.valueOf(i), points[i],
                            Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 255, 0), 1);
                }
            }

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

        // Wie weit ragt der gewünschte Ausschnitt über den Frame-Rand hinaus?
        int padLeft = Math.max(0, -offsetX);
        int padTop = Math.max(0, -offsetY);
        int padRight = Math.max(0, (offsetX + squareSize) - frame.cols());
        int padBottom = Math.max(0, (offsetY + squareSize) - frame.rows());

        // Den tatsächlich im Frame liegenden Teil des gewünschten Bereichs bestimmen.
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
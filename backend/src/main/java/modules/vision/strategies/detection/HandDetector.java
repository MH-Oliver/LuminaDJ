package modules.vision.strategies.detection;

import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HandDetector {

    private final Net yoloNet;
    private final List<String> outBlobNames;

    public HandDetector() {
        File cfgFile = new File("backend/src/main/resources/yolo/cross-hands-yolov4-tiny.cfg");
        File weightsFile = new File("backend/src/main/resources/yolo/cross-hands-yolov4-tiny.weights");

        if (!cfgFile.exists()) {
            cfgFile = new File("src/main/resources/yolo/cross-hands-yolov4-tiny.cfg");
            weightsFile = new File("src/main/resources/yolo/cross-hands-yolov4-tiny.weights");
        }

        if (!cfgFile.exists() || !weightsFile.exists()) {
            throw new RuntimeException("[FEHLER] YOLO-Dateien nicht gefunden unter: " + cfgFile.getAbsolutePath());
        }

        this.yoloNet = Dnn.readNetFromDarknet(cfgFile.getAbsolutePath(), weightsFile.getAbsolutePath());
        this.yoloNet.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
        this.yoloNet.setPreferableTarget(Dnn.DNN_TARGET_CPU);
        Core.setNumThreads(1);

        this.outBlobNames = yoloNet.getUnconnectedOutLayersNames();
    }

    public Rect detectHand(Mat frame) {
        System.out.println("###### NEUE VERSION AKTIV ######");
        if (frame == null || frame.empty()) {
            return null;
        }

        Mat blob = null;
        Mat squareCanvas = null;
        List<Mat> result = new ArrayList<>();

        try {
            // --- Letterboxing: Bild proportional auf ein Quadrat bringen statt es zu verzerren ---
            // Ohne das würde blobFromImage das 16:9-Kamerabild direkt auf 416x416 stauchen,
            // was kompaktere Handformen (Faust etc.) stärker verzerrt als eine ausgestreckte Hand.
            int squareSize = Math.max(frame.cols(), frame.rows());
            int padX = (squareSize - frame.cols()) / 2;
            int padY = (squareSize - frame.rows()) / 2;

            squareCanvas = Mat.zeros(squareSize, squareSize, frame.type());
            Rect roiOnCanvas = new Rect(padX, padY, frame.cols(), frame.rows());
            frame.copyTo(new Mat(squareCanvas, roiOnCanvas));

            blob = Dnn.blobFromImage(squareCanvas, 1.0 / 255.0, new Size(416, 416), new Scalar(0, 0, 0), true, false);
            System.out.println("DEBUG: blob erstellt, dims=" + blob.dims() + " channels=" + blob.channels());
            System.out.flush();

            yoloNet.setInput(blob);
            System.out.println("DEBUG: setInput erfolgreich");
            System.out.flush();

            // Alle Output-Layer (bei tiny-YOLO typischerweise 2: 13x13 und 26x26 Grid) abfragen.
            List<Mat> rawOutputs = new ArrayList<>();
            yoloNet.forward(rawOutputs, outBlobNames);

            // Jeden Output-Layer einzeln von 3D/4D (z.B. 1 x N x (5+classes)) auf 2D (N x (5+classes)) reshapen,
            // BEVOR irgendein .size()/.rows()/.cols()-Zugriff passiert. Das war die eigentliche Ursache
            // des ursprünglichen "vector subscript out of range"-Crashs.
            result.clear();
            for (Mat rawOut : rawOutputs) {
                if (rawOut == null || rawOut.empty()) continue;

                Mat reshaped = rawOut;
                if (rawOut.dims() > 2) {
                    int rows = rawOut.size(rawOut.dims() - 2);
                    reshaped = rawOut.reshape(1, rows);
                }
                System.out.println("DEBUG: Output-Layer verarbeitet, rows=" + reshaped.rows() + " cols=" + reshaped.cols());
                result.add(reshaped);
            }
            System.out.flush();

            float maxConfidence = 0;
            Rect bestBoundingBox = null;

            for (Mat level : result) {
                if (level == null || level.empty()) continue;

                int rows = level.rows();
                int cols = level.cols();

                // YOLO-Ergebnisse müssen mindestens 5 Spalten haben (x, y, w, h, confidence)
                if (cols < 5) continue;

                // Wir wandeln die gesamte Auswertungs-Matrix sicher in ein Java-Array um,
                // um jegliche native C++ Vector-Out-Of-Bounds Fehler zu verhindern.
                float[] data = new float[rows * cols];
                level.get(0, 0, data);

                for (int j = 0; j < rows; j++) {
                    int baseIdx = j * cols;

                    // Modell-Output ist relativ (0..1) zum quadratischen Canvas, nicht zum Original-Frame.
                    float centerXSquare = data[baseIdx + 0] * squareSize;
                    float centerYSquare = data[baseIdx + 1] * squareSize;
                    float widthSquare   = data[baseIdx + 2] * squareSize;
                    float heightSquare  = data[baseIdx + 3] * squareSize;
                    float objConf = data[baseIdx + 4];

                    // Wenn es weitere Klassen-Scores gibt (Spalte 5+), multiplizieren wir sie ein
                    float confidence = objConf;
                    if (cols > 5) {
                        float classScore = data[baseIdx + 5];
                        confidence = objConf * classScore;
                        if (confidence <= 0.01f) {
                            confidence = objConf;
                        }
                    }

                    if (confidence > 0.25f && confidence > maxConfidence) {
                        maxConfidence = confidence;

                        // Padding wieder abziehen, um zurück in Original-Frame-Koordinaten zu kommen.
                        int left = (int) (centerXSquare - widthSquare / 2) - padX;
                        int top = (int) (centerYSquare - heightSquare / 2) - padY;

                        left = Math.max(0, left);
                        top = Math.max(0, top);
                        int w = Math.min((int) widthSquare, frame.cols() - left);
                        int h = Math.min((int) heightSquare, frame.rows() - top);

                        if (w > 10 && h > 10) {
                            bestBoundingBox = new Rect(left, top, w, h);
                        }
                    }
                }
            }
            return bestBoundingBox;

        } catch (Exception e) {
            System.err.println("[YOLO FEHLER ABGEFANGEN]: " + e.getMessage());
            e.printStackTrace(); // <-- das hinzufügen
            return null;
        } finally {
            if (blob != null) blob.release();
            if (squareCanvas != null) squareCanvas.release();
            for (Mat mat : result) {
                if (mat != null) mat.release();
            }
        }
    }
}
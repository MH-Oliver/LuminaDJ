package modules.vision.training;

import modules.vision.strategies.detection.DetectionStrategyMock;
import modules.vision.strategies.detection.PalmDetector;
import modules.vision.strategies.detection.HandLandmarkExtractor;
import modules.vision.strategies.live_feedback.SmartphoneKameraStrategy;
import modules.vision.structures.HandLandmarks;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import org.opencv.core.CvType;
import java.io.File;
import java.io.IOException;

/**
 * Schneller Zwischenstands-Test: zeigt live in einem Fenster das aktuelle Kamerabild mit
 * eingezeichnetem Landmark-Skelett und der erkannten Geste. Speichert NICHTS - reiner
 * Praxis-Check, ob Erkennung + Klassifikation zusammen gut genug funktionieren, bevor man
 * sich für hunderte Trainingsbeispiele oder einen bestimmten Ansatz festlegt.
 * <p>
 * Nutzung: nach ein paar gesammelten Beispielen pro Geste (siehe DataCollectorApp) einmal
 * TrainGestureClassifierApp laufen lassen, dann dieses Programm starten und die Gesten vor
 * die Kamera halten. Fenster schließen oder Strg+C zum Beenden.
 */
public class LiveGestureTestApp {

    private static final String WINDOW_NAME = "Live Gesture Test";

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public static void main(String[] args) throws IOException {
        File modelFile = new File("backend/src/main/resources/models/gesture_classifier.csv");
        if (!modelFile.exists()) {
            modelFile = new File("src/main/resources/models/gesture_classifier.csv");
        }
        if (!modelFile.exists()) {
            System.err.println("[FEHLER] Kein trainiertes Modell gefunden unter: " + modelFile.getAbsolutePath()
                    + "\nErst DataCollectorApp (ein paar Beispiele pro Geste) und dann TrainGestureClassifierApp laufen lassen.");
            return;
        }

        GestureClassifier classifier = GestureClassifier.load(modelFile, 5);
        System.out.println("[INFO] Klassifikator geladen (" + classifier.size() + " Beispiele).");

        SmartphoneKameraStrategy camera = new SmartphoneKameraStrategy(new DetectionStrategyMock());
        PalmDetector handDetector = new PalmDetector();
        HandLandmarkExtractor landmarkExtractor = new HandLandmarkExtractor();

        System.out.println("[INFO] Live-Test läuft. Halte Gesten vor die Kamera. Fenster schließen oder Strg+C zum Beenden.");

        while (true) {
            BufferedImage bufferedImage = camera.fetchSingleFrame();

            if (bufferedImage != null) {
                Mat frame = bufferedImageToMat(bufferedImage);
                Rect handRoi = handDetector.detectHand(frame);

                String statusText;
                Mat displayFrame;

                if (handRoi != null) {
                    handRoi = restrictToFrame(handRoi, frame.cols(), frame.rows());
                    HandLandmarks landmarks = landmarkExtractor.extractLandmarks(frame, handRoi);

                    if (landmarks != null) {
                        double[] features = GestureFeatureExtractor.toFeatureVector(landmarks);
                        GestureClassifier.Prediction prediction = classifier.classify(features);

                        statusText = String.format("%s (%.0f%%)", prediction.label(), prediction.confidence() * 100);
                        System.out.printf("[GESTE] %-15s (Konfidenz: %.0f%%)%n",
                                prediction.label(), prediction.confidence() * 100);

                        // Overlay MIT Landmark-Skelett bauen (wird nicht gespeichert, nur angezeigt)
                        displayFrame = HandLandmarkExtractor.drawLandmarksOverlay(frame, landmarks);
                    } else {
                        statusText = "Hand erkannt, keine Landmarks";
                        System.out.println("[GESTE] Hand erkannt, aber keine Landmarks (Konfidenz zu niedrig).");
                        displayFrame = frame.clone();
                    }
                } else {
                    statusText = "Keine Hand im Bild";
                    System.out.println("[GESTE] Keine Hand im Bild.");
                    displayFrame = frame.clone();
                }

                Imgproc.putText(displayFrame, statusText, new Point(20, 40),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(0, 255, 255), 2);

                HighGui.imshow(WINDOW_NAME, displayFrame);
                // waitKey ist nötig, damit das Fenster tatsächlich neu zeichnet/reagiert -
                // kurzer Wert (1ms), da wir die eigentliche Framerate schon über
                // Thread.sleep() weiter unten steuern.
                HighGui.waitKey(1);
                // WICHTIG: erst NACH waitKey() freigeben - HighGui hält intern eine Referenz
                // auf die Mat für Redraw-Events (z.B. bei Fenster-Resize), die während
                // waitKey() ausgewertet wird. Zu früh freigegeben -> "w and h must be > 0".
                displayFrame.release();

                frame.release();
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        HighGui.destroyAllWindows();
    }

    private static Mat bufferedImageToMat(BufferedImage bi) {
        BufferedImage convertedImg = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        convertedImg.getGraphics().drawImage(bi, 0, 0, null);

        byte[] data = ((DataBufferByte) convertedImg.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }

    private static Rect restrictToFrame(Rect rect, int maxWidth, int maxHeight) {
        int x = Math.max(0, rect.x);
        int y = Math.max(0, rect.y);
        int width = Math.min(maxWidth - x, rect.width);
        int height = Math.min(maxHeight - y, rect.height);
        return new Rect(x, y, width, height);
    }
}
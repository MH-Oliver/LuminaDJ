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
import java.util.List;

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
                List<PalmDetector.PalmDetection> detections = handDetector.detectAllPalms(frame);

                Mat displayFrame = frame.clone();

                if (detections.isEmpty()) {
                    System.out.println("[GESTE] Keine Hand im Bild.");
                    Imgproc.putText(displayFrame, "Keine Hand im Bild", new Point(20, 40),
                            Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(0, 255, 255), 2);
                } else {
                    System.out.println("[INFO] " + detections.size() + " Hand/Hände erkannt.");

                    int handIndex = 0;
                    for (PalmDetector.PalmDetection detection : detections) {
                        handIndex++;
                        Rect handRoi = restrictToFrame(detection.box(), frame.cols(), frame.rows());
                        HandLandmarks landmarks = landmarkExtractor.extractLandmarks(frame, handRoi);

                        String statusText;
                        if (landmarks != null) {
                            double[] features = GestureFeatureExtractor.toFeatureVector(landmarks);
                            GestureClassifier.Prediction prediction = classifier.classify(features);

                            statusText = String.format("Hand %d: %s (%.0f%%)", handIndex,
                                    prediction.label(), prediction.confidence() * 100);
                            System.out.printf("[GESTE] Hand %d: %-15s (Konfidenz: %.0f%%)%n",
                                    handIndex, prediction.label(), prediction.confidence() * 100);

                            HandLandmarkExtractor.drawLandmarksOnto(displayFrame, landmarks);
                        } else {
                            statusText = String.format("Hand %d: keine Landmarks", handIndex);
                            System.out.println("[GESTE] Hand " + handIndex + ": erkannt, aber keine Landmarks (Konfidenz zu niedrig).");
                        }

                        // Box + Status-Text pro Hand einzeichnen, damit man bei mehreren
                        // Händen zuordnen kann, welcher Text zu welcher Hand gehört.
                        Imgproc.rectangle(displayFrame, handRoi.tl(), handRoi.br(), new Scalar(255, 128, 0), 2);
                        Imgproc.putText(displayFrame, statusText, new Point(handRoi.x, Math.max(20, handRoi.y - 10)),
                                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(0, 255, 255), 2);
                    }
                }

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
                Thread.sleep(50);
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
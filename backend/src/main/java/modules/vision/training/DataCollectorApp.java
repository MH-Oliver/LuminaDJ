package modules.vision.training;

import modules.vision.strategies.detection.DetectionStrategyMock;
import modules.vision.strategies.detection.HandDetector;
import modules.vision.strategies.live_feedback.SmartphoneKameraStrategy;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DataCollectorApp {

    // OpenCV native Bibliothek laden
    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public static void main(String[] args) {
        // 1. Neuen Run-Ordner mit Zeitstempel anlegen
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File runDir = new File("backend/src/main/resources/training_data/run_" + timestamp);

        if (!runDir.exists() && !runDir.mkdirs()) {
            System.err.println("[FEHLER] Konnte Trainings-Ordner nicht erstellen: " + runDir.getAbsolutePath());
            return;
        }
        System.out.println("[INFO] Speichere ausgeschnittene Hände in: " + runDir.getAbsolutePath());

        // 2. Kamera-Strategie und YOLO HandDetector initialisieren
        SmartphoneKameraStrategy camera = new SmartphoneKameraStrategy(new DetectionStrategyMock());
        HandDetector handDetector = new HandDetector();

        int totalFramesToCapture = 50; // Anzahl der zu sammelnden Hand-Bilder pro Run
        int frameDelayMs = 500;        // 500ms Pause zwischen den Frames
        int framesCaptured = 0;

        System.out.println("\n[INFO] Starte Aufnahme in 3 Sekunden... Mach deine Geste vor die Kamera!");
        try {
            Thread.sleep(300);
        } catch (InterruptedException ignored) {}

        while (framesCaptured < totalFramesToCapture) {
            BufferedImage bufferedImage = camera.fetchSingleFrame();

            if (bufferedImage != null) {
                // Java BufferedImage in OpenCV Mat konvertieren
                Mat frame = bufferedImageToMat(bufferedImage);

                // YOLO nach einer Hand suchen lassen
                System.out.println("Frame-Check: " + frame.size() + " channels=" + frame.channels() + " empty=" + frame.empty());
                Rect handRoi = handDetector.detectHand(frame);

                if (handRoi != null) {
                    // Sicherstellen, dass das Rechteck innerhalb des Bildes liegt
                    handRoi = restrictToFrame(handRoi, frame.cols(), frame.rows());

                    if (handRoi.width > 10 && handRoi.height > 10) {
                        // Hand-Bereich aus dem Bild ausschneiden
                        Mat handMat = new Mat(frame, handRoi);

                        // Als PNG im Run-Ordner abspeichern
                        String fileName = String.format("hand_%04d.png", framesCaptured);
                        File outputFile = new File(runDir, fileName);

                        Imgcodecs.imwrite(outputFile.getAbsolutePath(), handMat);
                        System.out.println("[GESPEICHERT] " + fileName + " (ROI: " + handRoi.width + "x" + handRoi.height + ")");

                        framesCaptured++;
                        handMat.release();
                    }
                } else {
                    System.out.println("[WARNUNG] Keine Hand im Bild erkannt. Übersprungen.");
                }

                frame.release();
            } else {
                System.err.println("[FEHLER] Konnte kein Bild von der Kamera empfangen.");
            }

            try {
                Thread.sleep(frameDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("==================================================");
        System.out.println("[ERFOLG] Run beendet! " + framesCaptured + " Hand-Bilder gesammelt.");
        System.out.println("==================================================");
    }

    /**
     * Hilfsmethode: Konvertiert ein Java BufferedImage in eine OpenCV Mat.
     */
    private static Mat bufferedImageToMat(BufferedImage bi) {
        // Konvertiere zu Standard-RGB, falls es ein anderes Format hat
        BufferedImage convertedImg = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        convertedImg.getGraphics().drawImage(bi, 0, 0, null);

        byte[] data = ((DataBufferByte) convertedImg.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }

    /**
     * Hilfsmethode: Begrenzt das Rechteck exakt auf die Bilddimensionen, um Out-of-Bounds-Fehler zu verhindern.
     */
    private static Rect restrictToFrame(Rect rect, int maxWidth, int maxHeight) {
        int x = Math.max(0, rect.x);
        int y = Math.max(0, rect.y);
        int width = Math.min(maxWidth - x, rect.width);
        int height = Math.min(maxHeight - y, rect.height);
        return new Rect(x, y, width, height);
    }
}
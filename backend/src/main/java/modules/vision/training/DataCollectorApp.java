package modules.vision.training;

import modules.vision.strategies.detection.DetectionStrategyMock;
import modules.vision.strategies.detection.HandDetector;
import modules.vision.strategies.detection.HandLandmarkExtractor;
import modules.vision.strategies.live_feedback.SmartphoneKameraStrategy;
import modules.vision.structures.HandLandmarks;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DataCollectorApp {

    // Feste Liste der zu unterscheidenden Gesten. Ordnername = Label fürs spätere Training.
    // Bewusst auf Gesten beschränkt, die sich in der Fingerkonfiguration klar unterscheiden
    // (nicht nur durch Rotation/Blickwinkel), damit die Klassen gut trennbar sind.
    private static final String[] GESTURES = {
            "offene_hand",
            "faust",
            "peace",
            "daumen_hoch",
            "zeigefinger"
    };

    // OpenCV native Bibliothek laden
    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public static void main(String[] args) {
        // 1. Geste auswählen, die in diesem Run gesammelt wird
        String gestureLabel = chooseGestureLabel();

        // 2. Neuen Run-Ordner mit Label + Zeitstempel anlegen
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File runDir = new File("backend/src/main/resources/training_data/" + gestureLabel + "/run_" + timestamp);

        if (!runDir.exists() && !runDir.mkdirs()) {
            System.err.println("[FEHLER] Konnte Trainings-Ordner nicht erstellen: " + runDir.getAbsolutePath());
            return;
        }
        System.out.println("[INFO] Geste: " + gestureLabel);
        System.out.println("[INFO] Speichere ausgeschnittene Hände in: " + runDir.getAbsolutePath());

        File debugDir = new File(runDir, "debug_landmarks");
        debugDir.mkdirs();

        // 3. Kamera-Strategie, YOLO HandDetector und Landmark-Extractor initialisieren
        SmartphoneKameraStrategy camera = new SmartphoneKameraStrategy(new DetectionStrategyMock());
        HandDetector handDetector = new HandDetector();
        HandLandmarkExtractor landmarkExtractor = new HandLandmarkExtractor(0.65f);

        int totalFramesToCapture = 50; // Anzahl der zu sammelnden Hand-Bilder pro Run
        int frameDelayMs = 500;        // 500ms Pause zwischen den Frames
        int framesCaptured = 0;

        System.out.println("\n[INFO] Starte Aufnahme in 3 Sekunden... Mach die Geste '" + gestureLabel + "' vor die Kamera!");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {}

        while (framesCaptured < totalFramesToCapture) {
            BufferedImage bufferedImage = camera.fetchSingleFrame();

            if (bufferedImage != null) {
                // Java BufferedImage in OpenCV Mat konvertieren
                Mat frame = bufferedImageToMat(bufferedImage);

                // YOLO nach einer Hand suchen lassen
                Rect handRoi = handDetector.detectHand(frame);

                if (handRoi != null) {
                    // Sicherstellen, dass das Rechteck innerhalb des Bildes liegt
                    handRoi = restrictToFrame(handRoi, frame.cols(), frame.rows());

                    // --- Test der Landmark-Extraktion ---
                    String debugFileName = String.format("debug_%04d.png", framesCaptured);
                    //File debugCropFile = new File(debugDir, "crop_" + debugFileName);
                    HandLandmarks landmarks = landmarkExtractor.extractLandmarks(frame, handRoi, null);
                    if (landmarks != null) {
                        System.out.println("[LANDMARKS] confidence=" + landmarks.confidence()
                                + " wrist=" + landmarks.points()[0]
                                + " indexTip=" + landmarks.points()[8]);

                        HandLandmarkExtractor.saveDebugVisualization(frame, landmarks, new File(debugDir, debugFileName));
                    } else {
                        System.out.println("[LANDMARKS] Keine Landmarks erkannt (Konfidenz zu niedrig).");
                    }

                    if (handRoi.width > 10 && handRoi.height > 10) {
                        // Hand-Bereich aus dem Bild ausschneiden
                        Mat handMat = new Mat(frame, handRoi);

                        // Als PNG im Run-Ordner abspeichern
                        String fileName = String.format("%s_%04d.png", gestureLabel, framesCaptured);
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
        System.out.println("[ERFOLG] Run beendet! " + framesCaptured + " Bilder für Geste '" + gestureLabel + "' gesammelt.");
        System.out.println("==================================================");
    }

    /**
     * Fragt per Dialog ab, welche Geste in diesem Run gesammelt werden soll.
     * Bricht das Programm ab, falls der Nutzer den Dialog abbricht.
     */
    private static String chooseGestureLabel() {
        String label = (String) JOptionPane.showInputDialog(
                null,
                "Welche Geste wird in diesem Run aufgenommen?",
                "Geste auswählen",
                JOptionPane.QUESTION_MESSAGE,
                null,
                GESTURES,
                GESTURES[0]
        );

        if (label == null) {
            System.err.println("Abbruch durch Nutzer.");
            System.exit(0);
        }
        return label;
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
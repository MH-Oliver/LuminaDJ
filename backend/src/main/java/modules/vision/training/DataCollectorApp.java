package modules.vision.training;

import modules.vision.strategies.detection.HandLandmarkExtractor;
import modules.vision.strategies.detection.PalmDetector;
import modules.vision.strategies.live_feedback.CameraDiscoverer;
import modules.vision.structures.HandLandmarks;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sammelt gelabelte Trainingsdaten für die Gesten-Klassifikation. Pro erfolgreich erkannter
 * Geste wird EIN Debug-Bild (Original-Frame mit eingezeichnetem Landmark-Skelett) sowie der
 * normalisierte Feature-Vektor in der wachsenden CSV gespeichert.
 */
public class DataCollectorApp {

    // Feste Liste der zu unterscheidenden Gesten. Ordnername = Label fürs spätere Training.
    private static final String[] GESTURES = {
            "offene_hand",
            "faust",
            "peace",
            "daumen_hoch",
            "zeigefinger"
    };

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public static void main(String[] args) throws IOException {
        String gestureLabel = chooseGestureLabel();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File runDir = new File("backend/src/main/resources/training_data/" + gestureLabel + "/run_" + timestamp);

        if (!runDir.exists() && !runDir.mkdirs()) {
            System.err.println("[FEHLER] Konnte Trainings-Ordner nicht erstellen: " + runDir.getAbsolutePath());
            return;
        }
        System.out.println("[INFO] Geste: " + gestureLabel);
        System.out.println("[INFO] Speichere Debug-Bilder in: " + runDir.getAbsolutePath());

        // Eine gemeinsame, wachsende CSV-Datei für ALLE Gesten
        File landmarksCsvFile = new File("backend/src/main/resources/training_data/gesture_landmarks.csv");

        System.out.println("[INFO] Suche Kamera im Netzwerk...");
        String cameraIp = CameraDiscoverer.resolveCameraIp();
        if (cameraIp == null) {
            System.err.println("[FEHLER] Keine Kamera gefunden.");
            return;
        }

        String cameraUrl = "http://" + cameraIp + "/shot.jpg";
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

        PalmDetector palmDetector = new PalmDetector();
        HandLandmarkExtractor landmarkExtractor = new HandLandmarkExtractor();

        int totalExamplesToCapture = 50;
        int frameDelayMs = 100;
        int examplesCaptured = 0;

        System.out.println("\n[INFO] Starte Aufnahme in 3 Sekunden... Mach die Geste '" + gestureLabel + "' vor die Kamera!");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {}

        while (examplesCaptured < totalExamplesToCapture) {
            BufferedImage bufferedImage = null;

            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(cameraUrl)).build();
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    bufferedImage = ImageIO.read(new ByteArrayInputStream(response.body()));
                }
            } catch (Exception e) {
                System.err.println("[FEHLER] Konnte kein Bild von der Kamera empfangen: " + e.getMessage());
            }

            if (bufferedImage != null) {
                Mat frame = bufferedImageToMat(bufferedImage);
                Rect handRoi = palmDetector.detectHand(frame);

                if (handRoi != null) {
                    handRoi = restrictToFrame(handRoi, frame.cols(), frame.rows());

                    HandLandmarks landmarks = landmarkExtractor.extractLandmarks(frame, handRoi);
                    if (landmarks != null) {
                        String debugFileName = String.format("%s_%04d.png", gestureLabel, examplesCaptured);
                        HandLandmarkExtractor.saveDebugVisualization(frame, landmarks, new File(runDir, debugFileName));

                        double[] featureVector = GestureFeatureExtractor.toFeatureVector(landmarks);
                        GestureClassifier.appendExample(landmarksCsvFile, gestureLabel, featureVector);

                        examplesCaptured++;
                        System.out.println("[GESPEICHERT] " + debugFileName + " (Konfidenz: " + landmarks.confidence() + ")");
                    } else {
                        System.out.println("[LANDMARKS] Keine Landmarks erkannt (Konfidenz zu niedrig). Übersprungen.");
                    }
                } else {
                    System.out.println("[WARNUNG] Keine Hand im Bild erkannt. Übersprungen.");
                }

                frame.release();
            }

            try {
                Thread.sleep(frameDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("==================================================");
        System.out.println("[ERFOLG] Run beendet! " + examplesCaptured + " Landmark-Beispiele für Geste '"
                + gestureLabel + "' in " + landmarksCsvFile.getName() + " gespeichert.");
        System.out.println("==================================================");
    }

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
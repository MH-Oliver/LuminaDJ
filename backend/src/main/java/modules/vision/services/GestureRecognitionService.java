package modules.vision.services;

import modules.vision.strategies.detection.HandLandmarkExtractor;
import modules.vision.strategies.detection.PalmDetector;
import modules.vision.structures.HandLandmarks;
import modules.vision.training.GestureClassifier;
import modules.vision.training.GestureFeatureExtractor;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GestureRecognitionService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final long POLL_INTERVAL_MS = 100;

    // Konstanten für die State-Machine
    private static final long HOLD_ACTIVATION_MS = 2000; // 2 Sekunden halten für Start
    private static final long HOLD_ACTION_MS = 2000;     // NEU: 2 Sekunden halten für die eigentliche Geste
    private static final long READY_WINDOW_MS = 7000;    // Auf 7s erhöht (5s Zeit zum Überlegen + 2s Halten)
    private static final double POSITION_TOLERANCE_PX = 60.0;
    private static final int K_NEAREST_NEIGHBORS = 5;

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

    private PalmDetector palmDetector;
    private HandLandmarkExtractor landmarkExtractor;
    private GestureClassifier gestureClassifier;

    private volatile String cameraUrl;
    private volatile boolean running = false;
    private Thread pollingThread;

    private final Map<String, Integer> confirmedGestureCounts = new ConcurrentHashMap<>();
    private volatile byte[] latestAnnotatedJpeg = null;

    private volatile String currentState = "IDLE";

    private final List<HoldTracker> activeHolds = new ArrayList<>();

    public synchronized void connect(String ipAddress) throws IOException {
        stop();
        if (palmDetector == null) {
            palmDetector = new PalmDetector();
            landmarkExtractor = new HandLandmarkExtractor();
            gestureClassifier = GestureClassifier.load(resolveGestureModelFile(), K_NEAREST_NEIGHBORS);
        }
        this.cameraUrl = "http://" + ipAddress + "/shot.jpg";
        this.confirmedGestureCounts.clear();
        this.activeHolds.clear();
        this.latestAnnotatedJpeg = null;
        this.currentState = "IDLE";
    }

    public synchronized void stop() {
        running = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
            try {
                pollingThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void resetGestureCounts() {
        confirmedGestureCounts.clear();
        activeHolds.clear();
        currentState = "IDLE";
    }

    public synchronized void pauseProcessing() {
        this.running = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
    }

    public synchronized void resumeProcessing() {
        if (!running && cameraUrl != null) {
            this.running = true;
            pollingThread = new Thread(this::pollLoop);
            pollingThread.setDaemon(true);
            pollingThread.start();
        }
    }

    public Snapshot getSnapshot() {
        byte[] jpeg = latestAnnotatedJpeg;
        if (jpeg == null) {
            return null;
        }
        return new Snapshot(jpeg, new HashMap<>(confirmedGestureCounts), currentState);
    }

    private void pollLoop() {
        System.out.println("GestureRecognitionService: Kamera-Auswertung gestartet");
        while (running) {
            try {
                BufferedImage bufferedImage = fetchFrame();
                if (bufferedImage != null) {
                    Mat frame = bufferedImageToMat(bufferedImage);
                    Mat annotated = processFrame(frame);

                    MatOfByte jpegBuffer = new MatOfByte();
                    Imgcodecs.imencode(".jpg", annotated, jpegBuffer);
                    latestAnnotatedJpeg = jpegBuffer.toArray();

                    annotated.release();
                    frame.release();
                }
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("GestureRecognitionService: Verbindung zur Kamera fehlgeschlagen: " + e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private Mat processFrame(Mat frame) {
        List<PalmDetector.PalmDetection> detections = palmDetector.detectAllPalms(frame);
        long now = System.currentTimeMillis();
        List<HoldTracker> stillActive = new ArrayList<>();
        Mat annotated = frame.clone();
        int handIndex = 0;

        if (detections.isEmpty()) {
            checkTimeouts(now);
            return annotated;
        }

        for (PalmDetector.PalmDetection detection : detections) {
            handIndex++;
            Rect handRoi = restrictToFrame(detection.box(), frame.cols(), frame.rows());
            HandLandmarks landmarks = landmarkExtractor.extractLandmarks(frame, handRoi);
            String statusText;

            if (landmarks != null) {
                double[] features = GestureFeatureExtractor.toFeatureVector(landmarks);
                GestureClassifier.Prediction prediction = gestureClassifier.classify(features);
                Point wrist = landmarks.points()[0];
                String label = prediction.label();

                HoldTracker matched = findMatchingTracker(wrist);

                if (currentState.equals("IDLE")) {
                    if (label.equals("offene_hand")) {
                        HoldTracker fresh = new HoldTracker();
                        fresh.position = wrist;
                        fresh.firstSeenAt = now;
                        stillActive.add(fresh);
                        currentState = "ACTIVATING";
                        statusText = "ACTIVATING... Hold open hand!";
                    } else {
                        statusText = "Hand " + handIndex + ": " + label + " (Waiting for open hand)";
                    }
                }
                else if (currentState.equals("ACTIVATING")) {
                    if (matched != null && label.equals("offene_hand")) {
                        stillActive.add(matched);
                        if (now - matched.firstSeenAt >= HOLD_ACTIVATION_MS) {
                            currentState = "READY";
                            matched.readySince = now;
                            statusText = "READY! Make your gesture now.";
                            System.out.println("[GESTURE STATE] READY - Waiting for action gesture");
                        } else {
                            statusText = "ACTIVATING... " + (HOLD_ACTIVATION_MS - (now - matched.firstSeenAt)) + "ms left";
                        }
                    } else {
                        currentState = "IDLE";
                        statusText = "Activation cancelled.";
                    }
                }
                else if (currentState.equals("READY")) {
                    if (matched != null) {
                        stillActive.add(matched);

                        if (now - matched.readySince > READY_WINDOW_MS) {
                            currentState = "IDLE";
                            statusText = "Time expired. Back to IDLE.";
                        } else {
                            if (label.equals("offene_hand")) {
                                // Wenn der Nutzer zurück zur offenen Hand geht, den Action-Timer abbrechen
                                matched.actionLabel = null;
                                statusText = "READY... " + (READY_WINDOW_MS - (now - matched.readySince))/1000 + "s left";
                            } else {
                                // NEU: Eine Action-Geste wurde erkannt!
                                if (matched.actionLabel == null || !matched.actionLabel.equals(label)) {
                                    // Startet den 2-Sekunden Action-Timer
                                    matched.actionLabel = label;
                                    matched.actionStartedAt = now;
                                }

                                long actionHoldTime = now - matched.actionStartedAt;
                                if (actionHoldTime >= HOLD_ACTION_MS) {
                                    // Geste wurde 2 Sekunden lang gehalten -> Aktion bestätigen!
                                    confirmedGestureCounts.merge(label, 1, Integer::sum);
                                    System.out.println("[GESTE BESTÄTIGT] " + label);
                                    currentState = "IDLE";
                                    statusText = "ACTION: " + label + "!";
                                } else {
                                    // Geste wird gerade gehalten (Countdown anzeigen)
                                    statusText = "HOLD " + label.toUpperCase() + "... " + (HOLD_ACTION_MS - actionHoldTime) + "ms";
                                }
                            }
                        }
                    } else {
                        currentState = "IDLE";
                        statusText = "Hand moved. Back to IDLE.";
                    }
                } else {
                    statusText = "Hand " + handIndex + ": " + label;
                }

                HandLandmarkExtractor.drawLandmarksOnto(annotated, landmarks);
            } else {
                statusText = String.format("Hand %d: keine Landmarks", handIndex);
            }

            Imgproc.rectangle(annotated, handRoi.tl(), handRoi.br(), new Scalar(255, 128, 0), 2);
            Imgproc.putText(annotated, statusText, new Point(handRoi.x, Math.max(20, handRoi.y - 10)),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(0, 255, 255), 2);
        }

        activeHolds.clear();
        activeHolds.addAll(stillActive);

        checkTimeouts(now);

        Imgproc.putText(annotated, "STATUS: " + currentState, new Point(10, 30),
                Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(0, 0, 255), 2);

        return annotated;
    }

    private void checkTimeouts(long now) {
        if (activeHolds.isEmpty() && !currentState.equals("IDLE")) {
            currentState = "IDLE";
            System.out.println("[GESTURE STATE] Hand lost. Resetting to IDLE.");
        }
        for(HoldTracker t : activeHolds) {
            if (currentState.equals("READY") && (now - t.readySince > READY_WINDOW_MS)) {
                currentState = "IDLE";
                activeHolds.clear();
                System.out.println("[GESTURE STATE] 7s Window expired. Resetting to IDLE.");
                break;
            }
        }
    }

    private HoldTracker findMatchingTracker(Point position) {
        for (HoldTracker tracker : activeHolds) {
            double dx = tracker.position.x - position.x;
            double dy = tracker.position.y - position.y;
            if (Math.sqrt(dx * dx + dy * dy) <= POSITION_TOLERANCE_PX) {
                return tracker;
            }
        }
        return null;
    }

    private BufferedImage fetchFrame() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cameraUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return ImageIO.read(new ByteArrayInputStream(response.body()));
            }
        } catch (Exception e) {
            latestAnnotatedJpeg = null;
            System.err.println("Fehler beim Abrufen des Einzelbildes: " + e.getMessage());
        }
        return null;
    }

    private static File resolveGestureModelFile() throws IOException {
        File modelFile = new File("backend/src/main/resources/models/gesture_classifier.csv");
        if (!modelFile.exists()) {
            modelFile = new File("src/main/resources/models/gesture_classifier.csv");
        }
        if (!modelFile.exists()) {
            throw new IOException("Kein trainiertes Gesten-Modell gefunden unter: " + modelFile.getAbsolutePath()
                    + " - erst DataCollectorApp und TrainGestureClassifierApp laufen lassen.");
        }
        return modelFile;
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
        return new Rect(x, y, Math.max(0, width), Math.max(0, height));
    }

    private static class HoldTracker {
        Point position;
        long firstSeenAt;
        long readySince;

        // NEU: Felder für den Timer der Action-Geste
        String actionLabel = null;
        long actionStartedAt = 0;
    }

    public record Snapshot(byte[] jpegImage, Map<String, Integer> confirmedGestureCounts, String status) {}
}
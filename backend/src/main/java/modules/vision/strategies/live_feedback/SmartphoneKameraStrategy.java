package modules.vision.strategies.live_feedback;

import modules.music.structures.Track;
import modules.vision.strategies.core.DetectionStrategy;
import modules.vision.strategies.core.LiveFeedbackStrategy;
import modules.vision.structures.FeedbackResult;
import modules.vision.structures.FrameDataDTO;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live-Feedback wird über eine HTTP-Schnittstelle zu einem Smartphone realisiert.
 * Die Kamera-Daten werden gemäß einer FrameRate durch eine DetectionStrategy ausgewertet.
 */
@Service
public class SmartphoneKameraStrategy implements LiveFeedbackStrategy {

    private final String cameraUrl;
    private final DetectionStrategy detectionStrategy;
    private final HttpClient httpClient;

    private volatile boolean isRunning = false;
    private Thread evaluationThread;

    private final List<Integer> intensityHistory = new CopyOnWriteArrayList<>();

    private final int FRAME_INTERVAL_MS = 10000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    public SmartphoneKameraStrategy(DetectionStrategy detectionStrategy) {
        String resolvedIpAndPort = CameraDiscoverer.resolveCameraIp();

        this.cameraUrl = "http://" + resolvedIpAndPort + "/shot.jpg";
        this.detectionStrategy = detectionStrategy;
        this.httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    }

    /**
     * Hier wird in einem eigenen Thread die Kamera-Auswertung realisiert.
     * Dabei wird je nach Frame-Rate ein HTTP Request an das Smartphone gesendet, der das aktuelle Bild abfragt.
     * Dieses Bild wird dann entsprechend der Detection-Strategy ausgewertet und das Ergebnis in einer History gespeichert.
     */
    @Override
    public void startParallelEvaluation(Track song) {
        isRunning = true;
        intensityHistory.clear();

        evaluationThread = new Thread(() -> {
            System.out.println("Smartphone-Kamera gestartet für Song: " + song.id());

            while (isRunning) {
                try {
                    // 1. Bild als Byte-Array vom Handy laden
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(cameraUrl))
                            .timeout(REQUEST_TIMEOUT)
                            .build();
                    HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                    if (response.statusCode() == 200) {
                        byte[] imageBytes = response.body();
                        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                        BufferedImage bufferedImage = ImageIO.read(bais);

                        if (bufferedImage != null) {
                            FrameDataDTO frameData = detectionStrategy.analyse(bufferedImage);
                            System.out.println("Live-Frame-Data: " + frameData);

                            intensityHistory.add(frameData.intensity());
                            System.out.println("Live-Vibe gemessen: " + frameData.intensity());
                        } else {
                            System.err.println("Konnte das Bild nicht decodieren.");
                        }
                    }

                    Thread.sleep(this.FRAME_INTERVAL_MS);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Verbindung zur Kamera fehlgeschlagen: " + e.getMessage());
                    try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        });
        evaluationThread.start();
    }

    /**
     * Hier wird das FeedbackResult berechnet.
     * Hierzu wird der Durchschnitt der Mesungen für die Intensity als Intensity übergeben.
     * Der Trend ist positiv,
     * wenn das letzte Frame eine höhere Intensity als der Durchschnitt über den letzten Song hatte.
     */
    @Override
    public FeedbackResult stopAndGetResult() {
        isRunning = false;
        if (evaluationThread != null) {
            evaluationThread.interrupt();
            try {
                evaluationThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return calculateFinalFeedback();
    }

    private FeedbackResult calculateFinalFeedback() {
        if (intensityHistory.isEmpty()) {
            return new FeedbackResult(true, 1.0);
        }

        double sum = 0;
        for (double val : intensityHistory) {
            sum += val;
        }
        double averageIntensity = sum / intensityHistory.size();
        boolean isPositiveTrend = intensityHistory.get(intensityHistory.size() - 1) >= averageIntensity;
        double normalizedAverageIntensity = Math.max(0.0, Math.min(1.0, averageIntensity / 100.0));

        System.out.printf("Song beendet. Ø Intensität: %.2f | Trend positiv: %b%n",
                averageIntensity, isPositiveTrend);

        return new FeedbackResult(isPositiveTrend, normalizedAverageIntensity);
    }
}
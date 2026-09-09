package modules.vision.strategies.live_feedback;

import org.springframework.stereotype.Component;

import javax.swing.JOptionPane;
import java.net.InetAddress;
import java.util.concurrent.*;

/**
 * Hilfsklasse, um automatisch die IP-Adresse von dem Smartphone mit dem Live-Kamera-Stream zu finden.
 */
@Component
public class CameraDiscoverer {

    /**
     * Falls eine IP-Adresse mit einem Kamera-Stream gefunden wurde, wird diese zurückgegeben.
     * @return IP-Adresse vom Kamera Stream
     */
    public static String resolveCameraIp() {
        return autoDetectCameraIp();
    }

    /**
     * Algorithmus zum Scannen der IP-Adresse.
     * Hier wird an alle Geräte im lokalen Netzwerk ein Request an den Port 8080 gesendet, und ein JPG-Bild als Ergebnis erwartet.
     * Wurde dies gefunden, wird die IP-Adresse von diesem Gerät zurückgegeben.
     * <p>
     * Um die Laufzeit auf max 3 Sekunden zu verringern, werden die Requests in 50 parallelen Threads ausgeführt.
     * @return IP-Adresse oder null
     */
    private static String autoDetectCameraIp() {
        System.out.println("Suche automatisch nach der Smartphone-Kamera im WLAN...");
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            String subnet = localIp.substring(0, localIp.lastIndexOf('.') + 1);
            try (ExecutorService executor = Executors.newFixedThreadPool(50)) {
                CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
                int submittedTasks = 0;

                for (int i = 1; i < 255; i++) {
                    String targetIp = subnet + i;
                    completionService.submit(() -> {
                        try {
                            String testUrl = "http://" + targetIp + ":8080/shot.jpg";
                            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(testUrl).openConnection();
                            connection.setConnectTimeout(300);
                            connection.setReadTimeout(300);
                            connection.setRequestMethod("HEAD");

                            String contentType = connection.getContentType();
                            if (connection.getResponseCode() == 200 && contentType != null && contentType.startsWith("image/")) {
                                return targetIp + ":8080";
                            }
                        } catch (Exception ignored) {
                        }
                        return null;
                    });
                    submittedTasks++;
                }
                for (int i = 0; i < submittedTasks; i++) {
                    String result = completionService.take().get();
                    if (result != null) {
                        executor.shutdownNow();
                        System.out.println("✅ Kamera automatisch gefunden unter: " + result);
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Auto-Scan: " + e.getMessage());
        }
        return null;
    }
}
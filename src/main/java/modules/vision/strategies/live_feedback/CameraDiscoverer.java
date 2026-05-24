package modules.vision.strategies.live_feedback;

import javax.swing.JOptionPane;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Hilfsklasse, um automatisch die IP-Adresse von dem Smartphone mit dem Live-Kamera-Stream zu finden.
 */
public class CameraDiscoverer {

    /**
     * Falls eine IP-Adresse mit einem Kamera-Stream gefunden wurde, wird diese zurückgegeben.
     * Ansonsten kann diese über einen Dialog eingegeben werden, falls dies auch nicht passiert erfolgt ein sofortiger Programm-Abbruch.
     * @return IP-Adresse vom Kamera Stream
     */
    public static String resolveCameraIp() {
        String ip = autoDetectCameraIp();

        if (ip == null) {
            ip = JOptionPane.showInputDialog(
                    null,
                    "Keine Kamera im WLAN gefunden.\nBitte IP der Webcam-App manuell eintragen:",
                    "Kamera verbinden",
                    JOptionPane.QUESTION_MESSAGE
            );

            if (ip == null || ip.trim().isEmpty()) {
                System.err.println("Abbruch durch Nutzer.");
                System.exit(0);
            }
        }
        return ip.replace("http://", "").replace("/shot.jpg", "");
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

            // Try-With-Ressources: Threads werdem im Anschluss alle automatisch wieder geschlossen.
            try (ExecutorService executor = Executors.newFixedThreadPool(50)) {
                List<Future<String>> futures = new ArrayList<>();

                for (int i = 1; i < 255; i++) {
                    String targetIp = subnet + i;
                    futures.add(executor.submit(() -> {
                        try {
                            String testUrl = "http://" + targetIp + ":8080/shot.jpg";
                            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(testUrl).openConnection();
                            connection.setConnectTimeout(300);
                            connection.setReadTimeout(300);
                            connection.setRequestMethod("HEAD");

                            if (connection.getResponseCode() == 200 && connection.getContentType().startsWith("image/")) {
                                return targetIp + ":8080";
                            }
                        } catch (Exception ignored) {
                        }
                        return null;
                    }));
                }

                // Ergebnisse auswerten
                for (Future<String> future : futures) {
                    String result = future.get(); // Wartet auf das Ergebnis dieses Threads
                    if (result != null) {
                        executor.shutdownNow(); // Bricht alle noch laufenden Suchanfragen sofort ab
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
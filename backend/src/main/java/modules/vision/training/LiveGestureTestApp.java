package modules.vision.training;

import modules.vision.services.GestureRecognitionService;
import modules.vision.strategies.live_feedback.CameraDiscoverer;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.IOException;

/**
 * Schneller Zwischenstands-Test: nutzt nun direkt den GestureRecognitionService.
 * Zeigt live in einem Fenster das aktuelle Kamerabild mit eingezeichnetem Landmark-Skelett
 * und loggt die bestätigten Gesten (5-Sekunden-Hold) in der Konsole.
 */
public class LiveGestureTestApp {

    private static final String WINDOW_NAME = "Live Gesture Test";

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public static void main(String[] args) throws IOException {
        System.out.println("[INFO] Suche Kamera im Netzwerk...");
        String cameraIp = CameraDiscoverer.resolveCameraIp();

        if (cameraIp == null) {
            System.err.println("[FEHLER] Keine Kamera gefunden. Bitte App auf dem Smartphone prüfen.");
            return;
        }

        System.out.println("[INFO] Verbinde GestureRecognitionService mit " + cameraIp + "...");
        GestureRecognitionService gestureService = new GestureRecognitionService();
        gestureService.connect(cameraIp);

        System.out.println("[INFO] Live-Test läuft. Fenster schließen oder Strg+C zum Beenden.");

        while (true) {
            GestureRecognitionService.Snapshot snapshot = gestureService.getSnapshot();

            if (snapshot != null) {
                Mat displayFrame = Imgcodecs.imdecode(new MatOfByte(snapshot.jpegImage()), Imgcodecs.IMREAD_COLOR);

                if (!displayFrame.empty()) {
                    HighGui.imshow(WINDOW_NAME, displayFrame);
                    int key = HighGui.waitKey(1);
                    displayFrame.release();
                    if (key == 27) {
                        break;
                    }
                }
                if (!snapshot.confirmedGestureCounts().isEmpty()) {
                    System.out.println("[BESTÄTIGTE GESTEN] " + snapshot.confirmedGestureCounts());
                }
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        gestureService.stop();
        HighGui.destroyAllWindows();
        System.exit(0);
    }
}
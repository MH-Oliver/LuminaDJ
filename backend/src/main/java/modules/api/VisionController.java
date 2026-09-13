package modules.api;

import modules.vision.services.GestureRecognitionService;
import modules.vision.strategies.live_feedback.CameraDiscoverer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

/**
 * Controller zur Steuerung der Smartphone-Kamera und der Gestenerkennung.
 * Wird verwendet, um das Live-Bild im Frontend anzuzeigen und Kamera-Verbindungen herzustellen.
 */
@RestController
@RequestMapping("/vision")
@CrossOrigin(origins = "*")
public class VisionController {

    private final CameraDiscoverer cameraDiscoverer;
    private final GestureRecognitionService gestureService;

    public VisionController(CameraDiscoverer cameraDiscoverer, GestureRecognitionService gestureService) {
        this.cameraDiscoverer = cameraDiscoverer;
        this.gestureService = gestureService;
    }

    /**
     * Sucht im lokalen WLAN automatisch nach einem Smartphone, das die Kamera-App für LuminaDJ geöffnet hat.
     */
    @GetMapping("/deviceFound")
    public ResponseEntity<?> deviceFound() {
        try {
            String foundIp = CameraDiscoverer.resolveCameraIp();
            if (foundIp == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Es konnte automatisch keine Kamera im Netzwerk gefunden werden."));
            }
            return ResponseEntity.ok(Map.of("ip", foundIp));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Interner Fehler beim Kamera-Scan."));
        }
    }

    /**
     * Verbindet das Backend fest mit der übergebenen IP-Adresse der Smartphone-Kamera.
     */
    @PostMapping("/selectedDevice")
    public ResponseEntity<?> selectedDevice(@RequestBody Map<String, String> payload) {
        String ipAddress = payload.get("ip");
        System.out.println("Verbinde mit Device IP: " + ipAddress);
        try {
            gestureService.connect(ipAddress);
            return ResponseEntity.ok(Map.of("connectedIp", ipAddress));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Verbindung fehlgeschlagen: " + e.getMessage()));
        }
    }

    /**
     * Liefert das aktuelle Kamerabild (als Base64-String) inklusive der aktuell erkannten
     * Gesten für die Anzeige in der Benutzeroberfläche.
     */
    @GetMapping("/currentFrame")
    public ResponseEntity<Map<String, Object>> currentFrame() {
        GestureRecognitionService.Snapshot snapshot = gestureService.getSnapshot();
        if (snapshot == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Noch kein Bild verfügbar - ist eine Kamera verbunden?"));
        }
        String base64Image = Base64.getEncoder().encodeToString(snapshot.jpegImage());
        return ResponseEntity.ok(Map.of(
                "image", "data:image/jpeg;base64," + base64Image,
                "gestures", snapshot.confirmedGestureCounts(),
                "status", snapshot.status()
        ));
    }

    /**
     * Setzt die Zähler für die erkannten Gesten zurück.
     */
    @PostMapping("/resetGestures")
    public ResponseEntity<Void> resetGestures() {
        gestureService.resetGestureCounts();
        return ResponseEntity.ok().build();
    }

    /**
     * Pausiert oder reaktiviert die Kamera-Bildverarbeitung, um Ressourcen zu sparen,
     * falls das Kamerabild im Frontend eingeklappt wird.
     */
    @PostMapping("/toggleState")
    public ResponseEntity<Map<String, Boolean>> toggleState(@RequestBody Map<String, Boolean> payload) {
        boolean active = payload.getOrDefault("active", true);
        if (active) {
            gestureService.resumeProcessing();
        } else {
            gestureService.pauseProcessing();
        }
        return ResponseEntity.ok(Map.of("isActive", active));
    }
}
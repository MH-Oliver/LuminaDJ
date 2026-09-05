package modules.api;

import modules.vision.services.GestureRecognitionService;
import modules.vision.strategies.live_feedback.CameraDiscoverer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

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

    @GetMapping("/deviceFound")
    public ResponseEntity<?> deviceFound() {
        try {
            String foundIp = CameraDiscoverer.resolveCameraIp();
            if (foundIp == null) {
                // Sauberer Fehler statt null-Response
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Es konnte automatisch keine Kamera im Netzwerk gefunden werden."));
            }
            return ResponseEntity.ok(Map.of("ip", foundIp));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Interner Fehler beim Kamera-Scan."));
        }
    }

    @PostMapping("/selectedDevice")
    public ResponseEntity<?> selectedDevice(@RequestBody Map<String, String> payload) {
        String ipAddress = payload.get("ip");
        System.out.println("Verbinde mit Device IP: " + ipAddress);
        try {
            gestureService.connect(ipAddress);
            return ResponseEntity.ok(Map.of("connectedIp", ipAddress));
        } catch (Exception e) {
            // StackTrace abfangen und als Message an das Frontend geben
            return ResponseEntity.badRequest().body(Map.of("error", "Verbindung fehlgeschlagen: " + e.getMessage()));
        }
    }

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
                "status", snapshot.status() // NEU: Status an Frontend senden
        ));
    }

    @PostMapping("/resetGestures")
    public ResponseEntity<Void> resetGestures() {
        gestureService.resetGestureCounts();
        return ResponseEntity.ok().build();
    }

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
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
    public ResponseEntity<Map<String, String>> deviceFound() {
        String foundIp = CameraDiscoverer.resolveCameraIp();

        // Wir verpacken die IP sauber in ein JSON-Objekt
        return ResponseEntity.ok(Map.of("ip", foundIp));
    }

    @PostMapping("/selectedDevice")
    public ResponseEntity<Map<String, Object>> selectedDevice(@RequestBody Map<String, String> payload) {
        String ipAddress = payload.get("ip");
        System.out.println("Verbinde mit Device IP: " + ipAddress);

        try {
            gestureService.connect(ipAddress);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        return ResponseEntity.ok(Map.of("connectedIp", ipAddress));
    }

    /**
     * Liefert das aktuelle Kamerabild (mit eingezeichnetem Landmark-Skelett, Base64-kodiert
     * als data-URL) sowie die Gesten, die über das 5-Sekunden-Hold-Verfahren bislang
     * bestätigt wurden (Label -> Anzahl seit dem letzten Reset).
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
                "gestures", snapshot.confirmedGestureCounts()
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
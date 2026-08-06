package modules.api;

import modules.vision.strategies.live_feedback.CameraDiscoverer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/vision")
@CrossOrigin(origins = "*")
public class VisionController {

    private final CameraDiscoverer cameraDiscoverer;

    public VisionController(CameraDiscoverer cameraDiscoverer) {
        this.cameraDiscoverer = cameraDiscoverer;
    }

    @GetMapping("/deviceFound")
    public ResponseEntity<Map<String, String>> deviceFound() {
        String foundIp = CameraDiscoverer.resolveCameraIp();

        // Wir verpacken die IP sauber in ein JSON-Objekt
        return ResponseEntity.ok(Map.of("ip", foundIp));
    }

    @PostMapping("/selectedDevice")
    public ResponseEntity<Map<String, String>> selectedDevice(@RequestBody Map<String, String> payload) {
        String ipAddress = payload.get("ip");

        // TODO: Verbindung zum ausgewählten Device herstellen
        System.out.println("Verbinde mit Device IP: " + ipAddress);

        return ResponseEntity.ok(Map.of("connectedIp", ipAddress));
    }
}
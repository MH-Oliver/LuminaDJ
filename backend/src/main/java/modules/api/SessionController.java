package modules.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/session")
@CrossOrigin(origins = "*")
public class SessionController {

    // TODO: Binde hier deinen DjSessionController oder SpotifyAdapter per Konstruktor ein
    // private final DjSessionController sessionController;

    @GetMapping("/update")
    public ResponseEntity<Map<String, Object>> updateSession() {
        // TODO: Aktuellen Status vom SpotifyAdapter abfragen
        Map<String, Object> currentSong = Map.of(
                "Name", "Californication",
                "Author", "Red Hot Chili Peppers",
                "Album-Bild", "https://example.com/cover.jpg",
                "Song-Länge", 329733,
                "Abspiel-position", 120500
        );

        return ResponseEntity.ok(Map.of("currentSong", currentSong));
    }

    @PostMapping("/skipSong")
    public ResponseEntity<Map<String, String>> skipSong() {
        // TODO: Im SpotifyAdapter die neue Methode zum Skippen aufrufen
        // String nextSongName = spotifyAdapter.skipToNext();

        return ResponseEntity.ok(Map.of("nextSong", "Can't Stop"));
    }

    @PostMapping("/edit")
    public ResponseEntity<Map<String, Object>> editSession() {
        // TODO: Song pausieren und die aktuelle Timeline zurückgeben
        // sessionController.pause();

        Map<String, Object> currentTimeline = Map.of(
                "status", "paused",
                "message", "Song pausiert. Bereit für Edits."
        );

        return ResponseEntity.ok(currentTimeline);
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelSession() {
        // TODO: Song pausieren und Session komplett abbrechen/beenden
        // sessionController.cancel();

        return ResponseEntity.ok().build();
    }
}
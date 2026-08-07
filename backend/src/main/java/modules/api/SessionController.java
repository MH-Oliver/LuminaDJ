package modules.api;

import modules.core.DjSessionController;
import modules.music.structures.Track;
import modules.music.strategies.music_player.spotify.SpotifyAdapter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/session")
@CrossOrigin(origins = "*")
public class SessionController {

    private final ActiveSessionService sessionService;

    // Wir lassen uns den Session-Speicher von Spring Boot geben
    public SessionController(ActiveSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/update")
    public ResponseEntity<Map<String, Object>> updateSession() {
        // Holen des WIRKLICH laufenden Controllers
        DjSessionController sessionController = sessionService.getActiveSession();

        // Sicherheits-Check: Falls noch keine Musik läuft
        if (sessionController == null || sessionController.getCurrentTrack() == null) {
            return ResponseEntity.ok(Map.of("currentSong", Map.of(
                    "Name", "Wird gestartet...",
                    "Author", "LuminaDJ",
                    "Album-Bild", "https://via.placeholder.com/150/1e1e1e/ffffff?text=Loading",
                    "Song-Länge", 0,
                    "Abspiel-position", 0
            )));
        }

        Track currentTrack = sessionController.getCurrentTrack();
        String coverUrl = "https://via.placeholder.com/150/1e1e1e/ffffff?text=Kein+Cover";
        long durationMs = 0;

        // Cover über die Spotify-API holen
        try {
            if (SpotifyAdapter.spotifyApi != null) {
                var spotifyTrack = SpotifyAdapter.spotifyApi.getTrack(currentTrack.id()).build().execute();
                if (spotifyTrack != null) {
                    durationMs = spotifyTrack.getDurationMs();
                    if (spotifyTrack.getAlbum() != null && spotifyTrack.getAlbum().getImages().length > 0) {
                        coverUrl = spotifyTrack.getAlbum().getImages()[0].getUrl();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Konnte Spotify-Cover nicht laden: " + e.getMessage());
        }

        Map<String, Object> songData = Map.of(
                "Name", currentTrack.name(),
                "Author", currentTrack.author(),
                "Album-Bild", coverUrl,
                "Song-Länge", durationMs,
                "Abspiel-position", sessionController.getPlayer().getPlaybackPosition()
        );

        return ResponseEntity.ok(Map.of("currentSong", songData));
    }

    @PostMapping("/skipSong")
    public ResponseEntity<Map<String, String>> skipSong() {
        DjSessionController sessionController = sessionService.getActiveSession();
        if (sessionController != null) {
            sessionController.getPlayer().skip();
            // Dem Backend 1 Sekunde Zeit geben, um den neuen Song zu laden
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

            if (sessionController.getCurrentTrack() != null) {
                return ResponseEntity.ok(Map.of("nextSong", sessionController.getCurrentTrack().name()));
            }
        }
        return ResponseEntity.ok(Map.of("nextSong", "Wird geladen..."));
    }

    @PostMapping("/edit")
    public ResponseEntity<Map<String, Object>> editSession() {
        DjSessionController sessionController = sessionService.getActiveSession();
        if (sessionController != null) {
            sessionController.getPlayer().pause();
        }
        Map<String, Object> currentTimeline = Map.of(
                "status", "paused",
                "message", "Song pausiert. Bereit für Edits."
        );
        return ResponseEntity.ok(currentTimeline);
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelSession() {
        DjSessionController sessionController = sessionService.getActiveSession();
        if (sessionController != null) {
            sessionController.getPlayer().pause();
        }
        return ResponseEntity.ok().build();
    }
}
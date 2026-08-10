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
                "Abspiel-position", sessionController.getPlayer().getPlaybackPosition(),
                "isPlaying", sessionController.getPlayer().isPlaying()
        );

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("currentSong", songData);

        if (sessionController.getContext() != null) {
            response.put("startTime", sessionController.getContext().startTime().toString());

            // 1:1 die gewählte Länge weitergeben
            response.put("totalMinutes", sessionController.getContext().totalMinutes());
        }

        return ResponseEntity.ok(response);
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
            sessionController.stopSession(); // Beendet das Playback UND die Schleife
        }
        Map<String, Object> currentTimeline = Map.of(
                "status", "stopped",
                "message", "Session gestoppt. Bereit für Edits."
        );
        return ResponseEntity.ok(currentTimeline);
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelSession() {
        DjSessionController sessionController = sessionService.getActiveSession();
        if (sessionController != null) {
            sessionController.stopSession(); // Beendet das Playback UND die Schleife
            sessionService.setActiveSession(null); // Gibt die Session komplett frei
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/favorite")
    public ResponseEntity<Map<String, Object>> toggleFavorite(@RequestBody Map<String, Boolean> payload) {
        DjSessionController sessionController = sessionService.getActiveSession();

        if (sessionController != null && sessionController.getCurrentTrack() != null) {
            boolean isFavorite = payload.getOrDefault("isFavorite", false);
            Track currentTrack = sessionController.getCurrentTrack();

            // Führt den API Call zu Spotify aus
            sessionController.getPlayer().setTrackFavoriteStatus(currentTrack, isFavorite);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Favoriten-Status geändert"
            ));
        }

        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Keine aktive Session oder kein Track gefunden"
        ));
    }


    @PostMapping("/playPause")
    public ResponseEntity<Map<String, String>> togglePlayPause() {
        DjSessionController session = sessionService.getActiveSession();
        if (session != null) {
            if (session.getPlayer().isPlaying()) {
                session.getPlayer().pause();
            } else {
                session.getPlayer().resume();
            }
        }
        return ResponseEntity.ok(Map.of("status", "toggled"));
    }

    @PostMapping("/seek")
    public ResponseEntity<Map<String, String>> seek(@RequestBody Map<String, Object> payload) {
        DjSessionController session = sessionService.getActiveSession();
        if (session != null && payload.containsKey("position")) {
            // Kugelsicheres Parsing: Egal ob Jackson ein Integer oder Long liefert, es wird korrekt gecastet
            long positionMs = ((Number) payload.get("position")).longValue();
            session.getPlayer().seek(positionMs);
        }
        return ResponseEntity.ok(Map.of("status", "seeked"));
    }

    @PostMapping("/previous")
    public ResponseEntity<Map<String, String>> previous() {
        DjSessionController session = sessionService.getActiveSession();
        if (session != null) {
            // Springt einfach an den Anfang des Songs zurück
            session.getPlayer().seek(0);
        }
        return ResponseEntity.ok(Map.of("status", "restarted"));
    }
}
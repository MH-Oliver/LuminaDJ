package modules.api;

import modules.core.DjSessionController;
import modules.music.structures.Track;
import modules.music.strategies.music_player.spotify.SpotifyAdapter;
import modules.userContext.structures.UserContextDTO;
import modules.vision.services.GestureRecognitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.Map;

@RestController
@RequestMapping("/session")
@CrossOrigin(origins = "*")
public class SessionController {
    private final ActiveSessionService sessionService;
    private final GestureRecognitionService gestureService;

    private String cachedTrackId = null;
    private String cachedCoverUrl = "https://via.placeholder.com/150/1e1e1e/ffffff?text=Kein+Cover";
    private long cachedDurationMs = 0;

    public SessionController(ActiveSessionService sessionService, GestureRecognitionService gestureService) {
        this.sessionService = sessionService;
        this.gestureService = gestureService;
    }

    @GetMapping("/update")
    public ResponseEntity<Map<String, Object>> updateSession() {
        DjSessionController sessionController = sessionService.getActiveSession();

        if (sessionController == null || sessionController.getCurrentTrack() == null) {
            return ResponseEntity.ok(Map.of("currentSong", Map.of(
                    "Name", "Wird gestartet...",
                    "Author", "LuminaDJ",
                    "Album-Bild", "https://via.placeholder.com/150/1e1e1e/ffffff?text=Loading",
                    "Song-Länge", 0,
                    "Abspiel-position", 0,
                    "isPlaying", false
            )));
        }

        Track currentTrack = sessionController.getCurrentTrack();

        boolean isNewTrack = cachedTrackId == null || !cachedTrackId.equals(currentTrack.id());
        boolean hasFailedCover = cachedCoverUrl.contains("Kein+Cover");

        if (isNewTrack || hasFailedCover) {
            if (isNewTrack) {
                cachedTrackId = currentTrack.id();
                cachedDurationMs = 180000;
            }

            try {
                if (SpotifyAdapter.spotifyApi != null) {
                    String pureId = currentTrack.id().replace("spotify:track:", "");
                    var spotifyTrack = SpotifyAdapter.spotifyApi.getTrack(pureId).build().execute();
                    if (spotifyTrack != null) {
                        cachedDurationMs = spotifyTrack.getDurationMs();
                        if (spotifyTrack.getAlbum() != null && spotifyTrack.getAlbum().getImages().length > 0) {
                            cachedCoverUrl = spotifyTrack.getAlbum().getImages()[0].getUrl();
                        } else {
                            cachedCoverUrl = "https://via.placeholder.com/150/1e1e1e/ffffff?text=No+Cover+Found";
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Konnte Spotify-Daten nicht laden. Versuche es gleich erneut. Grund: " + e.getMessage());
                cachedCoverUrl = "https://via.placeholder.com/150/1e1e1e/ffffff?text=Kein+Cover";
            }
        }

        long currentProgress = sessionController.getPlayer().getPlaybackPosition();
        boolean isPlaying = sessionController.getPlayer().isPlaying();

        Map<String, Object> songData = Map.of(
                "Name", currentTrack.name(),
                "Author", currentTrack.author(),
                "Album-Bild", cachedCoverUrl,
                "Song-Länge", cachedDurationMs,
                "Abspiel-position", currentProgress,
                "isPlaying", isPlaying
        );

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("currentSong", songData);

        if (sessionController.getContext() != null) {
            response.put("startTime", sessionController.getContext().startTime().toString());
            response.put("totalMinutes", sessionController.getContext().totalMinutes());
            response.put("timeline", sessionController.getContext().timeline().getPhases());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/jump")
    public ResponseEntity<Map<String, String>> jumpSession(@RequestBody Map<String, Integer> payload) {
        DjSessionController sessionController = sessionService.getActiveSession();
        if (sessionController != null && payload.containsKey("elapsedMinutes")) {
            int elapsedMinutes = payload.get("elapsedMinutes");
            UserContextDTO c = sessionController.getContext();

            LocalTime newStartTime = LocalTime.now().minusMinutes(elapsedMinutes);

            sessionController.setContext(new UserContextDTO(
                    c.tempo(), c.location(), newStartTime, c.timeline(), c.songCooldownMinutes(), c.totalMinutes()
            ));

            sessionController.getPlayer().skip();
        }
        return ResponseEntity.ok(Map.of("status", "jumped"));
    }

    @PostMapping("/skipSong")
    public ResponseEntity<Map<String, String>> skipSong() {
        DjSessionController sessionController = sessionService.getActiveSession();
        if (sessionController != null) {
            sessionController.getPlayer().skip();
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
            sessionController.stopSession();
        }
        gestureService.pauseProcessing();
        cachedTrackId = null;
        cachedCoverUrl = "https://via.placeholder.com/150/1e1e1e/ffffff?text=Kein+Cover";
        return ResponseEntity.ok(Map.of("status", "stopped", "message", "Session gestoppt."));
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelSession() {
        DjSessionController sessionController = sessionService.getActiveSession();
        if (sessionController != null) {
            sessionController.stopSession();
            sessionService.setActiveSession(null);
        }
        gestureService.pauseProcessing();
        cachedTrackId = null;
        cachedCoverUrl = "https://via.placeholder.com/150/1e1e1e/ffffff?text=Kein+Cover";
        return ResponseEntity.ok().build();
    }

    @PostMapping("/favorite")
    public ResponseEntity<Map<String, Object>> toggleFavorite(@RequestBody Map<String, Boolean> payload) {
        DjSessionController sessionController = sessionService.getActiveSession();
        if (sessionController != null && sessionController.getCurrentTrack() != null) {
            boolean isFavorite = payload.getOrDefault("isFavorite", false);
            Track currentTrack = sessionController.getCurrentTrack();
            sessionController.getPlayer().setTrackFavoriteStatus(currentTrack, isFavorite);
            return ResponseEntity.ok(Map.of("success", true, "message", "Favoriten-Status geändert"));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Keine aktive Session"));
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
            long positionMs = ((Number) payload.get("position")).longValue();
            session.getPlayer().seek(positionMs);
        }
        return ResponseEntity.ok(Map.of("status", "seeked"));
    }

    @PostMapping("/previous")
    public ResponseEntity<Map<String, String>> previous() {
        DjSessionController session = sessionService.getActiveSession();
        if (session != null) {
            session.getPlayer().seek(0);
        }
        return ResponseEntity.ok(Map.of("status", "restarted"));
    }
}
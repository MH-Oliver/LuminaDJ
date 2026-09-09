package modules.api;

import modules.music.strategies.music_player.spotify.SpotifyAuthenticator;
import modules.music.structures.Genre;
import modules.userContext.factories.TimelineFactory;
import modules.userContext.structures.GenreTimeline;
import modules.userContext.structures.SessionVibe;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/music")
@CrossOrigin(origins = "*")
public class MusicController {

    private final SpotifyAuthenticator authenticator;

    public MusicController(SpotifyAuthenticator authenticator, TimelineFactory timelineFactory) {
        this.authenticator = authenticator;
    }

    @GetMapping("/spotify/url")
    public ResponseEntity<Map<String, String>> getSpotifyUrl() {
        if (authenticator.checkSavedToken()) {
            return ResponseEntity.ok(Map.of("status", "already_connected"));
        }
        return ResponseEntity.ok(Map.of("url", authenticator.getAuthorizationUrl()));
    }

    @GetMapping("/spotify/check")
    public ResponseEntity<Map<String, Boolean>> checkSpotifyConnection() {
        return ResponseEntity.ok(Map.of("connected", authenticator.isAuthenticated()));
    }

    @GetMapping("/loadPresets")
    public ResponseEntity<List<String>> loadPresets() {
        List<String> presets = Arrays.stream(SessionVibe.values()).map(Enum::name).toList();
        return ResponseEntity.ok(presets);
    }

    @GetMapping("/selectPreset")
    public ResponseEntity<Map<String, Object>> selectPreset(@RequestParam String name) {
        GenreTimeline genreTimeline = TimelineFactory.createTimelineForVibe(SessionVibe.valueOf(name));
        return ResponseEntity.ok(Map.of("timeline", genreTimeline));
    }

    @GetMapping("/loadGenre")
    public ResponseEntity<List<String>> loadGenre(@RequestParam(required = false, defaultValue = "") String query) {
        List<String> matchingGenres = Arrays.stream(Genre.values())
                .map(Enum::name)
                .filter(genreName -> genreName.toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(matchingGenres);
    }
}
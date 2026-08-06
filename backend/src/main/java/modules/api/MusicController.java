package modules.api;

import modules.music.strategies.music_player.spotify.SpotifyAuthenticator;
import modules.music.structures.Genre;
import modules.userContext.factories.TimelineFactory;
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

    // TODO: Binde hier deine echten Services per Konstruktor ein
     private final SpotifyAuthenticator authenticator;
     private final TimelineFactory timelineFactory;

    public MusicController(SpotifyAuthenticator authenticator, TimelineFactory timelineFactory) {
        this.authenticator = authenticator;
        this.timelineFactory = timelineFactory;
    }

    @GetMapping("/connectSpotify")
    public ResponseEntity<Map<String, Boolean>> connectSpotify() {
        // TODO: Aufruf von authenticator.authenticate();

        boolean success = authenticator.authenticate() != null ;

        return ResponseEntity.ok(Map.of("success", success));
    }

    @GetMapping("/loadPresets")
    public ResponseEntity<List<String>> loadPresets() {
        // TODO: Presets aus der TimelineFactory abfragen
        List<String> presets = List.of("Workout", "Chill Out", "Focus", "Party");

        return ResponseEntity.ok(presets);
    }

    @GetMapping("/selectPreset")
    public ResponseEntity<Map<String, Object>> selectPreset(@RequestParam String name) {
        // TODO: Das echte Preset-Objekt anhand des Namens laden
        System.out.println("Preset angefragt: " + name);

        Map<String, Object> mockTimeline = Map.of(
                "phases", List.of(
                        Map.of("genre", "TECHNO", "durationMinutes", 45, "transitionOutMinutes", 5),
                        Map.of("genre", "HIP_HOP", "durationMinutes", 35, "transitionOutMinutes", 5)
                )
        );

        return ResponseEntity.ok(Map.of("timeline", mockTimeline));
    }

    @GetMapping("/loadGenre")
    public ResponseEntity<List<String>> loadGenre(@RequestParam(required = false, defaultValue = "") String query) {
        // Sucht in deiner Genre.java nach Übereinstimmungen mit dem Such-String
        List<String> matchingGenres = Arrays.stream(Genre.values())
                .map(Enum::name)
                .filter(genreName -> genreName.toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(matchingGenres);
    }
}
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

/**
 * Controller für allgemeine Musik- und Setup-Anfragen.
 * Verwaltet die Spotify-Authentifizierung und liefert Vorgaben (Presets/Genres)
 * für das Frontend, damit der Nutzer seine Session konfigurieren kann.
 */
@RestController
@RequestMapping("/music")
@CrossOrigin(origins = "*")
public class MusicController {

    private final SpotifyAuthenticator authenticator;

    public MusicController(SpotifyAuthenticator authenticator, TimelineFactory timelineFactory) {
        this.authenticator = authenticator;
    }

    /**
     * Startet den Spotify-Login.
     * Gibt entweder die URL zur Spotify-Login-Seite zurück oder meldet, dass der Nutzer bereits eingeloggt ist.
     */
    @GetMapping("/spotify/url")
    public ResponseEntity<Map<String, String>> getSpotifyUrl() {
        if (authenticator.checkSavedToken()) {
            return ResponseEntity.ok(Map.of("status", "already_connected"));
        }
        return ResponseEntity.ok(Map.of("url", authenticator.getAuthorizationUrl()));
    }

    /**
     * Prüft, ob das Backend aktuell erfolgreich mit einem Spotify-Account verbunden ist.
     */
    @GetMapping("/spotify/check")
    public ResponseEntity<Map<String, Boolean>> checkSpotifyConnection() {
        return ResponseEntity.ok(Map.of("connected", authenticator.isAuthenticated()));
    }

    /**
     * Lädt den Benutzernamen des aktuell verbundenen Spotify-Accounts.
     * Wird in der Benutzeroberfläche oben rechts angezeigt.
     */
    @GetMapping("/spotify/profile")
    public ResponseEntity<Map<String, String>> getSpotifyProfile() {
        if (authenticator.isAuthenticated()) {
            try {
                String username = authenticator.getSpotifyApi().getCurrentUsersProfile().build().execute().getDisplayName();
                return ResponseEntity.ok(Map.of("username", username));
            } catch (Exception e) {
                System.err.println("Fehler beim Abrufen des Spotify-Profils: " + e.getMessage());
            }
        }
        return ResponseEntity.ok(Map.of("username", "Spotify User"));
    }

    /**
     * Lädt eine Liste aller vorgefertigten Stimmungs-Profile (z.B. "Party", "Chill").
     */
    @GetMapping("/loadPresets")
    public ResponseEntity<List<String>> loadPresets() {
        List<String> presets = Arrays.stream(SessionVibe.values()).map(Enum::name).toList();
        return ResponseEntity.ok(presets);
    }

    /**
     * Lädt die vordefinierte musikalische Zeitachse (Timeline) für ein ausgewähltes Preset.
     */
    @GetMapping("/selectPreset")
    public ResponseEntity<Map<String, Object>> selectPreset(@RequestParam String name) {
        GenreTimeline genreTimeline = TimelineFactory.createTimelineForVibe(SessionVibe.valueOf(name));
        return ResponseEntity.ok(Map.of("timeline", genreTimeline));
    }

    /**
     * Sucht nach verfügbaren Musik-Genres. Wird genutzt, wenn der Nutzer im Setup
     * ein eigenes Genre in die Timeline eintippt.
     */
    @GetMapping("/loadGenre")
    public ResponseEntity<List<String>> loadGenre(@RequestParam(required = false, defaultValue = "") String query) {
        List<String> matchingGenres = Arrays.stream(Genre.values())
                .map(Enum::name)
                .filter(genreName -> genreName.toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(matchingGenres);
    }
}
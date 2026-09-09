package modules.api;

import modules.music.strategies.music_player.spotify.SpotifyAuthenticator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpotifyCallbackController {

    private final SpotifyAuthenticator authenticator;

    public SpotifyCallbackController(SpotifyAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(@RequestParam("code") String code) {
        try {
            authenticator.exchangeCode(code);
            String html = "<html><head><title>LuminaDJ Login</title></head>" +
                    "<body style='background-color: #121212; color: #1DB954; font-family: sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0;'>" +
                    "<div style='text-align: center;'>" +
                    "<h2>Login erfolgreich!</h2>" +
                    "<p>LuminaDJ ist verbunden. Dieser Tab schließt sich in 2 Sekunden automatisch...</p>" +
                    "</div>" +
                    "<script>setTimeout(() => window.close(), 2000);</script>" +
                    "</body></html>";
            return ResponseEntity.ok().header("Content-Type", "text/html; charset=UTF-8").body(html);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Fehler beim Spotify Login: " + e.getMessage());
        }
    }
}
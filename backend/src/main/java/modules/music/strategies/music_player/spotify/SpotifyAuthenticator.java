package modules.music.strategies.music_player.spotify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.springframework.stereotype.Service;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

@Service
public class SpotifyAuthenticator {

    private final SpotifyApi spotifyApi;

    private final Preferences prefs = Preferences.userNodeForPackage(SpotifyAuthenticator.class);
    private static final String PREF_REFRESH_TOKEN = "spotify_refresh_token";

    public SpotifyAuthenticator() {
        Config conf = ConfigFactory.load();
        String clientId = conf.getString("spotify.clientId");
        String clientSecret = conf.getString("spotify.clientSecret");
        URI redirectUri = SpotifyHttpManager.makeUri(conf.getString("spotify.redirectUri"));

        this.spotifyApi = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRedirectUri(redirectUri)
                .build();
    }

    public SpotifyApi authenticate() {
        //prefs.remove(PREF_REFRESH_TOKEN); // <-- Muss für diesen Start noch drin bleiben!

        // 1. VERSUCH: Automatischer Login über sicher gespeichertes Token
        String savedRefreshToken = prefs.get(PREF_REFRESH_TOKEN, null);

        if (savedRefreshToken != null) {
            try {
                System.out.println("Gefundenes Refresh Token wird geladen...");
                spotifyApi.setRefreshToken(savedRefreshToken);

                AuthorizationCodeRefreshRequest refreshRequest = spotifyApi.authorizationCodeRefresh().build();
                AuthorizationCodeCredentials credentials = refreshRequest.execute();

                spotifyApi.setAccessToken(credentials.getAccessToken());

                if (credentials.getRefreshToken() != null) {
                    spotifyApi.setRefreshToken(credentials.getRefreshToken());
                    // Neues Token sicher abspeichern
                    prefs.put(PREF_REFRESH_TOKEN, credentials.getRefreshToken());
                }

                System.out.println("Erfolgreich automatisch im Hintergrund eingeloggt!");
                return spotifyApi;
            } catch (Exception e) {
                System.out.println("Automatischer Login fehlgeschlagen. Starte manuellen Login...");
                // Das fehlerhafte Token aus den Preferences löschen
                prefs.remove(PREF_REFRESH_TOKEN);
            }
        }

        // 2. VERSUCH: Vollautomatischer Browser-Login
        try {
            AuthorizationCodeUriRequest uriRequest = spotifyApi.authorizationCodeUri()
                    // 1. Die Scopes mit Leerzeichen getrennt:
                    .scope("user-modify-playback-state user-read-playback-state user-library-modify")
                    // 2. NEU: Zwingt Spotify, das Fenster IMMER anzuzeigen!
                    .show_dialog(true)
                    .build();

            URI uri = uriRequest.execute();

            // Webserver starten, der auf die Antwort von Spotify wartet
            String code = startLocalServerAndWaitForCode(uri);

            // Code gegen Token eintauschen
            AuthorizationCodeRequest authRequest = spotifyApi.authorizationCode(code).build();
            AuthorizationCodeCredentials credentials = authRequest.execute();

            spotifyApi.setAccessToken(credentials.getAccessToken());
            spotifyApi.setRefreshToken(credentials.getRefreshToken());

            // Das neue Refresh Token professionell im OS speichern (Keine Datei mehr!)
            prefs.put(PREF_REFRESH_TOKEN, credentials.getRefreshToken());

            System.out.println("Erfolgreich eingeloggt! Token wurde sicher gespeichert.");
            return spotifyApi;
        } catch (Exception e) {
            System.err.println("Fehler bei der Spotify Authentifizierung: " + e.getMessage());
            return null;
        }
    }

    /**
     * Startet einen temporären Webserver, öffnet den Browser und wartet auf den Code.
     */
    private String startLocalServerAndWaitForCode(URI loginUri) throws Exception {
        CompletableFuture<String> futureCode = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/callback", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String query = exchange.getRequestURI().getQuery();
                String code = null;

                if (query != null && query.contains("code=")) {
                    code = query.split("code=")[1].split("&")[0];
                }

                String responseText = "<html><body><h1 style='font-family: sans-serif; color: #1DB954;'>Login erfolgreich!</h1>" +
                        "<p style='font-family: sans-serif;'>LuminaDJ ist jetzt mit Spotify verbunden. Du kannst dieses Fenster schliessen.</p></body></html>";
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseText.getBytes().length);

                OutputStream os = exchange.getResponseBody();
                os.write(responseText.getBytes());
                os.close();

                if (code != null) {
                    futureCode.complete(code);
                } else {
                    futureCode.completeExceptionally(new RuntimeException("Kein Code in der URL gefunden."));
                }
            }
        });

        server.start();
        System.out.println("Warte auf Spotify-Login...");

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(loginUri);
        } else {
            System.out.println("Bitte öffne diesen Link manuell: \n" + loginUri);
        }

        String authCode = futureCode.get();
        server.stop(0);
        return authCode;
    }
}
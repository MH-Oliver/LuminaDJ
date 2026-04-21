package modules.music.strategies.music_player.spotify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class SpotifyAuthenticator {

    private static final String clientId = "99dedc035fd44247a60054585bae8cb8";
    private static final String clientSecret = "1ca0da34964f4a1f89f89966e0fdaef1";
    private static final URI redirectUri = SpotifyHttpManager.makeUri("http://127.0.0.1:8080/callback");
    private static final String TOKEN_FILE = "spotify_refresh_token.txt";

    private final SpotifyApi spotifyApi;

    public SpotifyAuthenticator() {
        this.spotifyApi = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRedirectUri(redirectUri)
                .build();
    }

    public SpotifyApi authenticate() {
        Path tokenPath = Paths.get(TOKEN_FILE);

        // 1. VERSUCH: Automatischer Login über gespeichertes Token
        if (Files.exists(tokenPath)) {
            try {
                System.out.println("Gefundenes Refresh Token wird geladen...");
                String savedRefreshToken = Files.readString(tokenPath).trim();

                spotifyApi.setRefreshToken(savedRefreshToken);
                AuthorizationCodeRefreshRequest refreshRequest = spotifyApi.authorizationCodeRefresh().build();
                AuthorizationCodeCredentials credentials = refreshRequest.execute();

                spotifyApi.setAccessToken(credentials.getAccessToken());

                if (credentials.getRefreshToken() != null) {
                    spotifyApi.setRefreshToken(credentials.getRefreshToken());
                    Files.writeString(tokenPath, credentials.getRefreshToken());
                }

                System.out.println("Erfolgreich automatisch im Hintergrund eingeloggt!");
                return spotifyApi;
            } catch (Exception e) {
                System.out.println("Automatischer Login fehlgeschlagen. Starte manuellen Login...");
            }
        }

        // 2. VERSUCH: Vollautomatischer Browser-Login
        try {
            AuthorizationCodeUriRequest uriRequest = spotifyApi.authorizationCodeUri()
                    .scope("user-modify-playback-state")
                    .build();

            URI uri = uriRequest.execute();

            // Webserver starten, der auf die Antwort von Spotify wartet
            String code = startLocalServerAndWaitForCode(uri);

            // Code gegen Token eintauschen
            AuthorizationCodeRequest authRequest = spotifyApi.authorizationCode(code).build();
            AuthorizationCodeCredentials credentials = authRequest.execute();

            spotifyApi.setAccessToken(credentials.getAccessToken());
            spotifyApi.setRefreshToken(credentials.getRefreshToken());

            Files.writeString(tokenPath, credentials.getRefreshToken());
            System.out.println("Erfolgreich eingeloggt! Token wurde gespeichert.");

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
        // Ein "Zukunfts-Objekt", das unseren Code halten wird
        CompletableFuture<String> futureCode = new CompletableFuture<>();

        // Server auf Port 8080 erstellen
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Den /callback Endpunkt definieren
        server.createContext("/callback", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String query = exchange.getRequestURI().getQuery();
                String code = null;

                // Code aus der URL extrahieren (?code=XYZ...)
                if (query != null && query.contains("code=")) {
                    code = query.split("code=")[1].split("&")[0];
                }

                // Dem Browser eine hübsche Bestätigung senden
                String responseText = "<html><body><h1 style='font-family: sans-serif; color: #1DB954;'>Login erfolgreich!</h1>" +
                        "<p style='font-family: sans-serif;'>LuminaDJ ist jetzt mit Spotify verbunden. Du kannst dieses Fenster schliessen.</p></body></html>";

                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseText.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseText.getBytes());
                os.close();

                // Den Code an unser Hauptprogramm übergeben
                if (code != null) {
                    futureCode.complete(code);
                } else {
                    futureCode.completeExceptionally(new RuntimeException("Kein Code in der URL gefunden."));
                }
            }
        });

        server.start();

        // Browser automatisch öffnen (falls unterstützt)
        System.out.println("Warte auf Spotify-Login...");
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(loginUri);
        } else {
            // Fallback, falls das Betriebssystem das automatische Öffnen nicht unterstützt
            System.out.println("Bitte öffne diesen Link manuell: \n" + loginUri);
        }

        // Das Programm pausiert hier, bis der Server den Code übergeben hat
        String authCode = futureCode.get();

        // Server wieder abschalten
        server.stop(0);

        return authCode;
    }
}
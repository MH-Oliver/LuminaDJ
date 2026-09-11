package modules.music.strategies.music_player.spotify;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.springframework.stereotype.Service;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.prefs.Preferences;

@Service
public class SpotifyAuthenticator {

    private final SpotifyApi spotifyApi;
    private final Preferences prefs = Preferences.userNodeForPackage(SpotifyAuthenticator.class);
    private static final String PREF_REFRESH_TOKEN = "spotify_refresh_token";

    // NEU: Speichert den Verifier zwischen dem Aufruf der URL und dem Callback
    private String currentCodeVerifier;

    public SpotifyAuthenticator() {
        Config conf = ConfigFactory.load();
        String clientId = conf.getString("spotify.clientId");
        URI redirectUri = SpotifyHttpManager.makeUri(conf.getString("spotify.redirectUri"));

        this.spotifyApi = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setRedirectUri(redirectUri)
                .build();
    }

    public SpotifyApi getSpotifyApi() {
        return this.spotifyApi;
    }

    public SpotifyApi authenticate() {
        if (checkSavedToken()) {
            return spotifyApi;
        }
        return null;
    }

    public boolean isAuthenticated() {
        return checkSavedToken();
    }

    public boolean checkSavedToken() {
        String savedRefreshToken = prefs.get(PREF_REFRESH_TOKEN, null);
        if (savedRefreshToken != null) {
            try {
                System.out.println("Gefundenes Refresh Token wird geladen...");
                spotifyApi.setRefreshToken(savedRefreshToken);

                var refreshRequest = spotifyApi.authorizationCodePKCERefresh().build();
                var credentials = refreshRequest.execute();

                spotifyApi.setAccessToken(credentials.getAccessToken());
                if (credentials.getRefreshToken() != null) {
                    spotifyApi.setRefreshToken(credentials.getRefreshToken());
                    prefs.put(PREF_REFRESH_TOKEN, credentials.getRefreshToken());
                }
                System.out.println("Erfolgreich automatisch im Hintergrund eingeloggt!");
                return true;
            } catch (Exception e) {
                System.out.println("Automatischer Login fehlgeschlagen: " + e.getMessage());
                prefs.remove(PREF_REFRESH_TOKEN);
            }
        }
        return false;
    }

    private void generatePKCE() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        byte[] codeVerifierBytes = new byte[32];
        secureRandom.nextBytes(codeVerifierBytes);
        this.currentCodeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifierBytes);
    }

    private String getCodeChallenge(String verifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] signature = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    public String getAuthorizationUrl() {
        try {
            generatePKCE(); // Schlüssel vor der Anfrage generieren
            String challenge = getCodeChallenge(this.currentCodeVerifier);

            AuthorizationCodeUriRequest uriRequest = spotifyApi.authorizationCodeUri()
                    .scope("user-modify-playback-state user-read-playback-state user-library-modify")
                    .show_dialog(true)
                    .code_challenge(challenge)
                    .code_challenge_method("S256")
                    .build();

            return uriRequest.execute().toString();
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Generieren der PKCE-URL", e);
        }
    }

    public void exchangeCode(String code) throws Exception {
        var authRequest = spotifyApi.authorizationCodePKCE(code, this.currentCodeVerifier).build();

        AuthorizationCodeCredentials credentials = authRequest.execute();
        spotifyApi.setAccessToken(credentials.getAccessToken());
        spotifyApi.setRefreshToken(credentials.getRefreshToken());

        prefs.put(PREF_REFRESH_TOKEN, credentials.getRefreshToken());
        System.out.println("Erfolgreich eingeloggt! Token wurde sicher gespeichert.");
    }
}
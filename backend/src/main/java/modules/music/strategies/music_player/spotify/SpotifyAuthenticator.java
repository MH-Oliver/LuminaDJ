package modules.music.strategies.music_player.spotify;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.springframework.stereotype.Service;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;

import java.net.URI;
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
                AuthorizationCodeRefreshRequest refreshRequest = spotifyApi.authorizationCodeRefresh().build();
                AuthorizationCodeCredentials credentials = refreshRequest.execute();

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

    public String getAuthorizationUrl() {
        AuthorizationCodeUriRequest uriRequest = spotifyApi.authorizationCodeUri()
                .scope("user-modify-playback-state user-read-playback-state user-library-modify")
                .show_dialog(true)
                .build();
        return uriRequest.execute().toString();
    }

    public void exchangeCode(String code) throws Exception {
        AuthorizationCodeRequest authRequest = spotifyApi.authorizationCode(code).build();
        AuthorizationCodeCredentials credentials = authRequest.execute();

        spotifyApi.setAccessToken(credentials.getAccessToken());
        spotifyApi.setRefreshToken(credentials.getRefreshToken());
        prefs.put(PREF_REFRESH_TOKEN, credentials.getRefreshToken());

        System.out.println("Erfolgreich eingeloggt! Token wurde sicher gespeichert.");
    }
}
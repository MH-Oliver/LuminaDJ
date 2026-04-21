package modules.music.strategies.music_player.spotify;

import com.google.gson.JsonArray;
import modules.music.strategies.core.MusicPlayerAdapter;
import modules.music.structures.Track;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.requests.data.player.PauseUsersPlaybackRequest;
import se.michaelthelin.spotify.requests.data.player.SetVolumeForUsersPlaybackRequest;
import se.michaelthelin.spotify.requests.data.player.StartResumeUsersPlaybackRequest;

public class SpotifyAdapter implements MusicPlayerAdapter {

    private final SpotifyApi spotifyApi;

    /**
     * Erstellt einen neuen Adapter mit einer bereits authentifizierten API-Instanz.
     */
    public SpotifyAdapter(SpotifyApi spotifyApi) {
        this.spotifyApi = spotifyApi;
    }

    @Override
    public void play(Track track) {
        try {
            // Spotify erwartet URIs im Format "spotify:track:ID"
            String trackUri = track.id().startsWith("spotify:track:")
                    ? track.id()
                    : "spotify:track:" + track.id();

            JsonArray uris = new JsonArray();
            uris.add(trackUri);

            StartResumeUsersPlaybackRequest playRequest = spotifyApi
                    .startResumeUsersPlayback()
                    .uris(uris)
                    .build();

            playRequest.execute();
            System.out.println("Spotify spielt jetzt: " + track.name() + " (" + track.author() + ")");
        } catch (Exception e) {
            handleError("Fehler beim Starten der Wiedergabe", e);
        }
    }

    @Override
    public void pause() {
        try {
            PauseUsersPlaybackRequest pauseRequest = spotifyApi
                    .pauseUsersPlayback()
                    .build();
            pauseRequest.execute();
            System.out.println("Wiedergabe pausiert.");
        } catch (Exception e) {
            handleError("Fehler beim Pausieren", e);
        }
    }

    @Override
    public void setVolume(int level) {
        try {
            // Level muss zwischen 0 und 100 liegen
            int volume = Math.max(0, Math.min(100, level));

            SetVolumeForUsersPlaybackRequest volumeRequest = spotifyApi
                    .setVolumeForUsersPlayback(volume)
                    .build();

            volumeRequest.execute();
            System.out.println("Lautstärke auf " + volume + "% gesetzt.");
        } catch (Exception e) {
            handleError("Fehler beim Setzen der Lautstärke", e);
        }
    }

    @Override
    public long getPlaybackPosition() {
        try {
            CurrentlyPlayingContext context = spotifyApi
                    .getInformationAboutUsersCurrentPlayback()
                    .build()
                    .execute();

            return (context != null) ? context.getProgress_ms() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // NEU
    @Override
    public void addToQueue(Track track) {
        try {
            // Wieder sicherstellen, dass das Format stimmt
            String trackUri = track.id().startsWith("spotify:track:")
                    ? track.id()
                    : "spotify:track:" + track.id();

            // Der API Call für die Warteschlange
            spotifyApi.addItemToUsersPlaybackQueue(trackUri).build().execute();

            System.out.println("Song zur Warteschlange hinzugefügt: " + track.name() + " (" + track.author() + ")");
        } catch (Exception e) {
            handleError("Fehler beim Hinzufügen zur Warteschlange", e);
        }
    }
    
    
    

    private void handleError(String message, Exception e) {
        System.err.println(message + ": " + e.getMessage());
        // Hier könnte man prüfen, ob das Token abgelaufen ist und ggf. eine
        // Neu-Authentifizierung über den SpotifyAuthenticator anstoßen.
    }
}
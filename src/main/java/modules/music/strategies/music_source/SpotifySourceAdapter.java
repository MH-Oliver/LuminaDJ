package modules.music.strategies.music_source;

import modules.music.strategies.core.MusicSourceAdapter;
import modules.music.strategies.music_player.spotify.SpotifyAdapter;
import modules.music.structures.Track;
import modules.prediction.structures.PredictedAttributes;
import se.michaelthelin.spotify.SpotifyApi;

public class SpotifySourceAdapter implements MusicSourceAdapter {

    private final SpotifyApi spotifyApi;

    public SpotifySourceAdapter() {
        this.spotifyApi = SpotifyAdapter.spotifyApi;
    }

    @Override
    public Track getNextSong(PredictedAttributes target, Track localCandidate) {
        try {
            String primaryArtist = localCandidate.author().split("[;,]")[0].trim();

            String searchQuery = "track:\"" + localCandidate.name() + "\" artist:\"" + primaryArtist + "\"";

            var searchResult = spotifyApi.searchTracks(searchQuery).limit(1).build().execute();

            if (searchResult.getItems().length > 0) {
                var freshTrack = searchResult.getItems()[0];

                return new Track(
                        freshTrack.getId(),
                        freshTrack.getName(),
                        freshTrack.getArtists()[0].getName(),
                        localCandidate.genre(),
                        localCandidate.features()
                );
            } else {
                return null; // Song existiert nicht mehr bei Spotify
            }

        } catch (Exception e) {
            System.err.println("SpotifySourceAdapter | Suche nach Track fehlgeschlagen: " + e.getMessage());
            return null;
        }
    }
}
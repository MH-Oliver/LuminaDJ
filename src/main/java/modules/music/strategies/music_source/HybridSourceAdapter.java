package modules.music.strategies.music_source;

import modules.music.strategies.core.MusicSourceAdapter;
import modules.music.structures.Track;
import modules.prediction.structures.PredictedAttributes;

import java.util.List;

public class HybridSourceAdapter implements MusicSourceAdapter {

    private final LocalSongDatabaseAdapter localKnn;
    private final MusicSourceAdapter spotifySearchApi;

    public HybridSourceAdapter(LocalSongDatabaseAdapter localKnn, MusicSourceAdapter spotifySearchApi) {
        this.localKnn = localKnn;
        this.spotifySearchApi = spotifySearchApi;
    }

    @Override
    public Track getNextSong(PredictedAttributes target, Track currentSong) {
        int maxAttempts = 5;
        List<Track> bestCandidates = localKnn.getTopK(target, currentSong, maxAttempts);

        for (int i = 0; i < bestCandidates.size(); i++) {
            Track candidate = bestCandidates.get(i);

            Track verifiedTrack = spotifySearchApi.getNextSong(target, candidate);

            if (verifiedTrack != null) {
                System.out.println("Hybdrid-Adapter | Gewählter Song: : " + verifiedTrack);
                return verifiedTrack;
            }
        }

        throw new IllegalArgumentException("Hybrid-Adapter | Es konnte kein Song gefunden werden, der auf Spotify existiert");
    }
}
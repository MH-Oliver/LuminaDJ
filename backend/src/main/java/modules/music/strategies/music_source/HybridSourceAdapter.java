package modules.music.strategies.music_source;

import modules.music.strategies.core.MusicSourceAdapter;
import modules.music.structures.Track;
import modules.prediction.structures.PredictedAttributes;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HybridSourceAdapter implements MusicSourceAdapter {

    private final LocalSongDatabaseAdapter localKnn;
    private final MusicSourceAdapter spotifySearchApi;
    private final Set<String> playedTrackIds = new HashSet<>();
    private final Set<String> playedTrackNames = new HashSet<>();

    public HybridSourceAdapter(LocalSongDatabaseAdapter localKnn, MusicSourceAdapter spotifySearchApi) {
        this.localKnn = localKnn;
        this.spotifySearchApi = spotifySearchApi;
    }

    @Override
    public Track getNextSong(PredictedAttributes target, Track currentSong) {
        playedTrackIds.add(currentSong.id());
        playedTrackNames.add(currentSong.name().toLowerCase());

        int poolSize = 50;
        List<Track> bestCandidates = localKnn.getTopK(target, currentSong, poolSize);

        bestCandidates.removeIf(track ->
                playedTrackIds.contains(track.id()) ||
                        playedTrackNames.contains(track.name().toLowerCase())
        );

        int subListSize = Math.min(10, bestCandidates.size());
        if (subListSize == 0) {
            System.out.println("Hybrid-Adapter | Reset der gespielten Tracks!");
            playedTrackIds.clear();
            playedTrackNames.clear();
            return getNextSong(target, currentSong);
        }

        List<Track> varietyPool = bestCandidates.subList(0, subListSize);
        Collections.shuffle(varietyPool);

        for (Track candidate : varietyPool) {
            Track verifiedTrack = spotifySearchApi.getNextSong(target, candidate);

            if (verifiedTrack != null) {
                System.out.println("Hybrid-Adapter | Gewählter Song: " + verifiedTrack.name());

                playedTrackIds.add(candidate.id());
                playedTrackNames.add(candidate.name().toLowerCase());

                playedTrackIds.add(verifiedTrack.id());
                playedTrackNames.add(verifiedTrack.name().toLowerCase());

                return verifiedTrack;
            }
        }

        throw new IllegalArgumentException("Hybrid-Adapter | Es konnte kein Song gefunden werden, der auf Spotify existiert");
    }
}
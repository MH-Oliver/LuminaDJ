package modules.music.services;

import modules.music.strategies.music_source.LocalSongDatabaseAdapter;
import modules.music.structures.Genre;
import modules.music.structures.Track;
import modules.prediction.structures.PredictedAttributes;

import java.util.HashMap;
import java.util.Map;

public class SessionBootstrapper {

    private final LocalSongDatabaseAdapter localDb;

    public SessionBootstrapper(LocalSongDatabaseAdapter localDb) {
        this.localDb = localDb;
    }

    public Track generateFirstTrack(Map<Genre, Double> genreWeights) {
        double totalWeight = 0;
        Map<String, Double> mixedFeatures = new HashMap<>();

        Genre dominantGenre = Genre.POP;
        double maxWeight = -1;

        for (Map.Entry<Genre, Double> entry : genreWeights.entrySet()) {
            Genre genre = entry.getKey();
            double weight = entry.getValue();

            if (weight > maxWeight) {
                maxWeight = weight;
                dominantGenre = genre;
            }

            PredictedAttributes centroid = localDb.getGenreCentroid(genre.getDisplayName());
            Map<String, Double> attr = centroid.features();

            for (Map.Entry<String, Double> featureEntry : attr.entrySet()) {
                mixedFeatures.merge(featureEntry.getKey(), featureEntry.getValue() * weight, Double::sum);
            }

            totalWeight += weight;
        }

        if (totalWeight == 0) totalWeight = 1;

        for (Map.Entry<String, Double> featureEntry : mixedFeatures.entrySet()) {
            mixedFeatures.put(featureEntry.getKey(), featureEntry.getValue() / totalWeight);
        }

        PredictedAttributes mixedAttributes = new PredictedAttributes(mixedFeatures);

        Track dummyTrack = new Track(
                dominantGenre.getSeedTrackId().isEmpty() ? "dummy-id" : dominantGenre.getSeedTrackId(),
                "Seed",
                dominantGenre.getDisplayName(),
                dominantGenre.getDisplayName(),
                localDb.getGenreCentroid(dominantGenre.getDisplayName()).features()
        );

        return localDb.getNextSong(mixedAttributes, dummyTrack);
    }
}
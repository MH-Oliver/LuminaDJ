package modules.prediction.strategies.prediction;

import modules.music.strategies.core.MusicSourceAdapter;
import modules.music.strategies.music_source.LocalSongDatabaseAdapter;
import modules.music.structures.Genre;
import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictedAttributes;
import modules.prediction.structures.PredictionFactor;
import modules.userContext.strategies.core.UserContextStrategy;
import modules.userContext.structures.UserContextDTO;

import java.util.HashMap;
import java.util.Map;

public class MacroCurveStrategy implements PredictionStrategy {

    private final LocalSongDatabaseAdapter localDb;
    private final UserContextStrategy contextStrategy;

    public MacroCurveStrategy(LocalSongDatabaseAdapter localDb, UserContextStrategy contextStrategy) {
        this.localDb = localDb;
        this.contextStrategy = contextStrategy;
    }

    // 1. GIB DER KURVE MEHR GEWICHT (z.B. 3.0), DAMIT SIE DIE HISTORY ÜBERSTIMMT
    @Override
    public double getWeight() {
        return 3.0;
    }

    @Override
    public PredictionFactor calculate(Track currentTrack) {
        UserContextDTO context = contextStrategy.getUserContext();

        // Vergangene Minuten berechnen (Fallback auf 0, falls start null ist)
        long elapsedMinutes = 0;
        if (context.startTime() != null) {
            elapsedMinutes = java.time.Duration.between(context.startTime(), java.time.LocalTime.now()).toMinutes();
            if(elapsedMinutes < 0) elapsedMinutes = 0;
        }

        Map<Genre, Double> currentWeights = context.timeline().getWeightsAt((double) elapsedMinutes);

        Map<String, Double> stringWeights = new HashMap<>();
        Map<String, Double> interpolatedCentroids = new HashMap<>();

        // 2. Ziel-Features (Centroids) interpolieren
        for (Map.Entry<Genre, Double> entry : currentWeights.entrySet()) {
            String genreName = entry.getKey().name().toLowerCase().replace('_', '-');
            double weight = entry.getValue();

            stringWeights.put(genreName, weight);

            PredictedAttributes centroid = localDb.getGenreCentroid(genreName);
            if (centroid != null && centroid.features() != null) {
                for (Map.Entry<String, Double> feature : centroid.features().entrySet()) {
                    interpolatedCentroids.merge(feature.getKey(), feature.getValue() * weight, Double::sum);
                }
            }
        }

        // 3. NEU: Absolute Ziele in relative Faktoren (Multiplikatoren) umrechnen!
        Map<String, Double> featureFactors = new HashMap<>();
        for (Map.Entry<String, Double> entry : interpolatedCentroids.entrySet()) {
            String featureName = entry.getKey();
            double targetValue = entry.getValue();
            double currentValue = currentTrack.features().getOrDefault(featureName, -1.0);

            if (currentValue > 0.0) {
                // Faktor berechnen (Ziel / Aktuell)
                featureFactors.put(featureName, targetValue / currentValue);
            } else {
                // Fallback, falls das Feature im aktuellen Song 0 ist
                featureFactors.put(featureName, 1.0);
            }
        }

        var predictionFactor = new PredictionFactor(featureFactors, stringWeights);
        System.out.println("MacroCurveStrategy: " + predictionFactor);
        return predictionFactor;
    }
}
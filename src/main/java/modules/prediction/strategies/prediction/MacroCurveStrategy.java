package modules.prediction.strategies.prediction;

import modules.music.strategies.music_source.LocalSongDatabaseAdapter;
import modules.music.structures.Genre;
import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictionFactor;
import modules.userContext.services.UserContextService;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public class MacroCurveStrategy implements PredictionStrategy {

    private final LocalSongDatabaseAdapter localDb;

    // Wir brauchen Zugriff auf die Datenbank, um die Centroids der Genres abzufragen
    public MacroCurveStrategy(LocalSongDatabaseAdapter localDb) {
        this.localDb = localDb;
    }

    @Override
    public double getWeight() {
        return 0.9;
    }

    @Override
    public PredictionFactor calculate(Track currentTrack) {
        var context = UserContextService.getInstance().getCurrentContext();

        // 1. Relative Zeit berechnen (Minuten seit Start)
        double elapsedMinutes = ChronoUnit.SECONDS.between(context.startTime(), LocalTime.now()) / 60.0;

        // 2. Aktuelle Genre-Mischung von der Timeline holen (z.B. 80% Rock, 20% Metal)
        Map<Genre, Double> genreWeights = context.timeline().getWeightsAt(elapsedMinutes);

        System.out.println("Macro-Curve-Strategy | Mischung folgender Genres:" + genreWeights);

        // 3. Einen gemischten Ziel-Vektor aus den Centroids berechnen
        Map<String, Double> targetFeatures = new HashMap<>();
        double totalWeight = 0;

        for (Map.Entry<Genre, Double> entry : genreWeights.entrySet()) {
            Genre genre = entry.getKey();
            double weight = entry.getValue();

            Map<String, Double> centroid = localDb.getGenreCentroid(genre.getDisplayName()).features();
            for (Map.Entry<String, Double> f : centroid.entrySet()) {
                targetFeatures.merge(f.getKey(), f.getValue() * weight, Double::sum);
            }
            totalWeight += weight;
        }

        // Durchschnittswerte bilden
        if (totalWeight > 0) {
            for (String key : targetFeatures.keySet()) {
                targetFeatures.put(key, targetFeatures.get(key) / totalWeight);
            }
        }

        // 4. Multiplikatoren (Faktor) für den aktuellen Song berechnen (
        // --> nicht nötig, da MacroCurve eigentlich nur einfluss auf das genrelle Genre
        Map<String, Double> multipliers = new HashMap<>();
        /*for (String key : currentTrack.features().keySet()) {
            double currentVal = currentTrack.features().getOrDefault(key, 0.0);
            double targetVal = targetFeatures.getOrDefault(key, currentVal);

            double factor = targetVal / Math.max(0.01, currentVal);
            multipliers.put(key, factor);
        }*/

        Map<String, Double> stringGenreWeights = new HashMap<>();
        for (Map.Entry<Genre, Double> entry : genreWeights.entrySet()) {
            stringGenreWeights.put(entry.getKey().getDisplayName(), entry.getValue());
        }

        var newPredictionFactor = new PredictionFactor(multipliers, stringGenreWeights);
        System.out.println("Macro-Curve-Strategy: " + newPredictionFactor);
        return newPredictionFactor;
    }
}
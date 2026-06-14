package modules.prediction.services;

import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.strategies.prediction.LiveFeedbackAdapter;
import modules.vision.structures.FeedbackResult;
import modules.prediction.structures.PredictedAttributes;
import modules.prediction.structures.PredictionFactor;

import java.util.*;

public class PredictionAggregator {
    private final List<PredictionStrategy> strategies;

    // Dependency Injection: Die Strategien werden von außen übergeben
    public PredictionAggregator(List<PredictionStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * Berechnet die neuen Attribute des nächsten Tracks.
     * <p>
     * Der Einfluss der Strategien wird auf Basis der Gewichte berechnet.
     * Am Ende wird der Durchschnitt des veränderten Wertes über alle Gewichte genommen.
     */

    public PredictedAttributes calculateNextAttributes(Track currentSong, FeedbackResult feedback) {
        Map<String, Double> baseValues = currentSong.features();
        Map<String, Double> finalValues = new HashMap<>();

        List<PredictionStrategy> runStrategies = new ArrayList<>(this.strategies);
        runStrategies.add(new LiveFeedbackAdapter(feedback, 0.8));

        for (String featureKey : baseValues.keySet()) {
            double baseVal = baseValues.get(featureKey);
            double newValue = baseVal;

            for (PredictionStrategy strategy : runStrategies) {
                PredictionFactor factor = strategy.calculate(currentSong);

                double multiplier = factor.features().getOrDefault(featureKey, 1.0);
                double weight = strategy.getWeight();

                newValue += ((baseVal * multiplier - baseVal) * weight);
            }

            finalValues.put(featureKey, newValue / runStrategies.size());
        }

        return new PredictedAttributes(finalValues);
    }
}

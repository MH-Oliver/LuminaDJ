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

        List<PredictionFactor> strategyFactors = runStrategies.stream()
                .map(predictionStrategy -> predictionStrategy.calculate(currentSong))
                .toList();

        for (String featureKey : baseValues.keySet()) {
            double baseVal = baseValues.get(featureKey);
            double totalDelta = 0.0;

            for (int i = 0; i < strategyFactors.size(); i++) {
                PredictionFactor factor = strategyFactors.get(i);
                double multiplier = factor.features().getOrDefault(featureKey, 1.0);
                double weight = runStrategies.get(i).getWeight();

                totalDelta += ((baseVal * multiplier) - baseVal) * weight;
            }

            double averageDelta = totalDelta / runStrategies.size();
            double newValue = baseVal + averageDelta;

            newValue = Math.max(0.0, Math.min(1.0, newValue));

            finalValues.put(featureKey, newValue);
        }

        return new PredictedAttributes(finalValues);
    }
}

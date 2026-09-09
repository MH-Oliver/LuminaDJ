package modules.prediction.strategies.prediction;

import modules.music.repositories.SessionHistoryRepository;
import modules.music.structures.HistoryEntry;
import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictionFactor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryStrategy implements PredictionStrategy {

    private final SessionHistoryRepository repository;
    private static final double SIGMA = 0.5;

    public HistoryStrategy(SessionHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public double getWeight() {
        return 0.2;
    }

    @Override
    public PredictionFactor calculate(Track x) {
        List<HistoryEntry> history = repository.getHistory();
        Map<String, Double> multipliers = new HashMap<>();

        if (history.isEmpty()) {
            for (String key : x.features().keySet()) {
                multipliers.put(key, 1.0);
            }
            return new PredictionFactor(multipliers);
        }

        for (String key : x.features().keySet()) {
            multipliers.put(key, calculateOptimalFactor(key, x, history));
        }

        var newPredictionFactor = new PredictionFactor(multipliers);
        System.out.println("History Strategy: " + newPredictionFactor);
        return newPredictionFactor;
    }

    /**
     * Berechnet den Anpassungsfaktor für ein spezifisches Attribut mittels Locally Weighted Learning.
     */
    private double calculateOptimalFactor(String featureKey, Track x, List<HistoryEntry> history) {
        double currentValue = x.features().getOrDefault(featureKey, 0.0);
        double weightedSum = 0;
        double totalWeight = 0;

        for (HistoryEntry entry : history) {
            Track xi = entry.track();
            double distance = calculateDistance(x, xi);
            double gaussianKernel = Math.exp(-(distance * distance) / (2 * SIGMA * SIGMA));
            double feedbackReward = 1.0;
            double wi = gaussianKernel * feedbackReward;

            weightedSum += wi * xi.features().getOrDefault(featureKey, 0.0);
            totalWeight += wi;
        }

        if (totalWeight == 0) return 1.0;
        double yHat = weightedSum / totalWeight;
        return yHat / Math.max(0.01, currentValue);
    }

    private double calculateDistance(Track x, Track xi) {
        double sum = 0;
        for (String key : x.features().keySet()) {
            double valX = x.features().getOrDefault(key, 0.0);
            double valXi = xi.features().getOrDefault(key, 0.0);
            sum += Math.pow(valX - valXi, 2);
        }
        return Math.sqrt(sum);
    }
}
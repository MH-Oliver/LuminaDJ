package modules.prediction.structures;

import java.util.HashMap;
import java.util.Map;

/**
 * Repräsentiert den Einfluss (Multiplikator) einer einzelnen Strategie
 */
public record PredictionFactor(
        Map<String, Double> features,
        Map<String, Double> genreWeights
) {
    public PredictionFactor(Map<String, Double> features) {
        this(features, new HashMap<>());
    }
}
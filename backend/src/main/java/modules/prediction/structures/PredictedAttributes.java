package modules.prediction.structures;

import java.util.HashMap;
import java.util.Map;

/**
 * Repräsentiert die berechneten Ziel-Attribute für den nächsten Song
 */
public record PredictedAttributes(
        Map<String, Double> features,
        Map<String, Double> genreWeights
) {
    public PredictedAttributes(Map<String, Double> features) {
        this(features, new HashMap<>());
    }
}
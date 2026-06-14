package modules.prediction.structures;

import java.util.Map;

/**
 * Repräsentiert die berechneten Ziel-Attribute für den nächsten Song
 */
public record PredictedAttributes(
        Map<String, Double> features
) {}

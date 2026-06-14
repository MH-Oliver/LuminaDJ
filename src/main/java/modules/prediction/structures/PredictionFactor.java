package modules.prediction.structures;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Repräsentiert den Einfluss (Multiplikator) einer einzelnen Strategie
 */
public record PredictionFactor (
        Map<String, Double> features
) {}
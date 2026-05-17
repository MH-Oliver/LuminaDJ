package modules.prediction.structures;

/**
 * Repräsentiert den Einfluss (Multiplikator) einer einzelnen Strategie
 * @param energyMultiplier
 * @param bpmMultiplier
 */
public record PredictionFactor(double energyMultiplier, double bpmMultiplier) {}
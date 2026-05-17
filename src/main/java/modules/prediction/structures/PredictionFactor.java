package modules.prediction.structures;

import java.util.Iterator;
import java.util.List;

/**
 * Repräsentiert den Einfluss (Multiplikator) einer einzelnen Strategie
 * @param energyMultiplier
 * @param bpmMultiplier
 */
public record PredictionFactor(double energyMultiplier, double bpmMultiplier) implements Iterable<Double> {

    @Override
    public Iterator<Double> iterator() {
        return List.of(energyMultiplier, bpmMultiplier).iterator();
    }
}
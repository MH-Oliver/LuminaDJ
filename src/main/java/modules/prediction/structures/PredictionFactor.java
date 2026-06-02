package modules.prediction.structures;

import java.util.Iterator;
import java.util.List;

/**
 * Repräsentiert den Einfluss (Multiplikator) einer einzelnen Strategie
 * @param energyMultiplier
 * @param bpmMultiplier
 */
public record PredictionFactor (
    double energyMultiplier,
    double bpmMultiplier,
    double danceabilityMultiplier,
    double acousticnessMultiplier,
    double instrumentalnessMultiplier,
    double speechinessMultiplier
) implements Iterable<Double> {

    @Override
    public Iterator<Double> iterator() {
        return List.of(
                energyMultiplier,
                bpmMultiplier,
                danceabilityMultiplier,
                acousticnessMultiplier,
                instrumentalnessMultiplier,
                speechinessMultiplier
        ).iterator();
    }
}
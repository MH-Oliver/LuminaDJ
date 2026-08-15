package modules.prediction;

import modules.music.structures.Track;
import modules.prediction.services.PredictionAggregator;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictedAttributes;
import modules.prediction.structures.PredictionFactor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PredictionAggregatorTest {

    @Test
    void testCalculateNextAttributes_ShouldCalculateAverageDeltaAndClamp() {
        Map<String, Double> baseFeatures = new HashMap<>();
        baseFeatures.put("energy", 0.5);
        Track currentSong = new Track("id", "Test Song", "Test Artist", "rock", baseFeatures);

        // Dummy Strategie 1: Möchte die Energy um +20% (Faktor 1.2) mit 100% Gewicht anheben
        PredictionStrategy strategy1 = new PredictionStrategy() {
            @Override public double getWeight() { return 1.0; }
            @Override public PredictionFactor calculate(Track track) {
                return new PredictionFactor(Map.of("energy", 1.2));
            }
        };

        PredictionStrategy strategy2 = new PredictionStrategy() {
            @Override public double getWeight() { return 1.0; }
            @Override public PredictionFactor calculate(Track track) {
                return new PredictionFactor(Map.of("energy", 1.4));
            }
        };

        PredictionAggregator aggregator = new PredictionAggregator(List.of(strategy1, strategy2));

        PredictedAttributes result = aggregator.calculateNextAttributes(currentSong);

        double resultEnergy = result.features().get("energy");

        assertTrue(resultEnergy > 0.5, "Energy hätte steigen müssen");
        assertTrue(resultEnergy <= 1.0, "Energy darf 1.0 nicht überschreiten (Clamping Check)");
    }
}
package modules.prediction;

import modules.music.repositories.SessionHistoryRepository;
import modules.music.structures.Track;
import modules.prediction.strategies.prediction.HistoryStrategy;
import modules.prediction.structures.PredictionFactor;
import modules.vision.structures.FeedbackResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryStrategyTest {

    @Test
    void testCalculate_EmptyHistory_ReturnsFactorOne() {
        // Arrange
        SessionHistoryRepository repo = new SessionHistoryRepository();
        HistoryStrategy strategy = new HistoryStrategy(repo);

        Map<String, Double> features = new HashMap<>();
        features.put("energy", 0.5);
        Track currentTrack = new Track("id", "Test", "Artist", "pop", features);

        // Act
        PredictionFactor factor = strategy.calculate(currentTrack);

        // Assert
        // Wenn keine Historie da ist, darf die Strategie die Werte nicht verfälschen (Faktor 1.0)
        assertEquals(1.0, factor.features().get("energy"), 0.001, "Faktor muss 1.0 bei leerer Historie sein");
    }

    @Test
    void testCalculate_WithPositiveHistory_ShiftsTowardsHistory() {
        SessionHistoryRepository repo = new SessionHistoryRepository();

        // 1. Wir tun so, als lief vorhin ein Song mit SEHR VIEL Energy (0.8), der mega ankam (0.9 Intensität)
        Map<String, Double> histFeatures = new HashMap<>();
        histFeatures.put("energy", 0.8);
        Track histTrack = new Track("id1", "Banger", "DJ", "edm", histFeatures);
        repo.addEntry(histTrack, new FeedbackResult(true, 0.9));

        HistoryStrategy strategy = new HistoryStrategy(repo);

        // 2. Unser aktueller Song hat wenig Energy (0.4)
        Map<String, Double> currentFeatures = new HashMap<>();
        currentFeatures.put("energy", 0.4);
        Track currentTrack = new Track("id2", "Boring Song", "Artist", "pop", currentFeatures);

        // Act
        PredictionFactor factor = strategy.calculate(currentTrack);

        // Assert
        double energyFactor = factor.features().get("energy");

        // Da der alte Song gut ankam, MUSS die Strategie dem Aggregator sagen:
        // "Erhöhe die Energy, wir müssen wieder in Richtung 0.8!"
        // Der Faktor muss also größer als 1.0 sein.
        assertTrue(energyFactor > 1.0, "Energy-Faktor sollte ansteigen, um sich dem erfolgreichen Song anzunähern");
    }
}
package modules.prediction.strategies.prediction;

import modules.music.repositories.SessionHistoryRepository;
import modules.music.structures.HistoryEntry;
import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictionFactor;

import java.util.List;
import java.util.function.ToDoubleFunction;

public class HistoryStrategy implements PredictionStrategy {

    private final SessionHistoryRepository repository;

    // Bandbreite (Sigma) des Gauss-Kernels
    private static final double SIGMA = 0.5;

    public HistoryStrategy(SessionHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public double getWeight() {
        return 0.4; // Relevanz der Historie im Gesamtmix
    }

    @Override
    public PredictionFactor calculate(Track x) {
        List<HistoryEntry> history = repository.getHistory();

        // Wenn noch kein Song in der Historie ist, verändern wir nichts
        if (history.isEmpty()) {
            return new PredictionFactor(1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        }

        // Berechnet die Faktoren dynamisch für alle 6 Parameter
        var newPredictionFactor = new PredictionFactor(
                calculateOptimalFactor(Track::energy, x, history),
                calculateOptimalFactor(Track::bpm, x, history),
                calculateOptimalFactor(Track::danceability, x, history),
                calculateOptimalFactor(Track::acousticness, x, history),
                calculateOptimalFactor(Track::instrumentalness, x, history),
                calculateOptimalFactor(Track::speechiness, x, history)
        );

        System.out.println("History Strategy: " + newPredictionFactor);
        return newPredictionFactor;
    }

    /**
     * Berechnet den Anpassungsfaktor für ein spezifisches Attribut mittels Locally Weighted Learning.
     */
    private double calculateOptimalFactor(ToDoubleFunction<Track> attributeExtractor, Track x, List<HistoryEntry> history) {
        double currentValue = attributeExtractor.applyAsDouble(x);
        double weightedSum = 0;
        double totalWeight = 0;

        for (HistoryEntry entry : history) {
            Track xi = entry.track();
            var feedback = entry.feedback();

            // 1. d(x, xi) - Normalisierte Euklidische Distanz
            double distance = calculateDistance(x, xi);

            // 2. K(x, xi) - Gauss-Kernel (Radial Basis Function)
            double gaussianKernel = Math.exp(-(distance * distance) / (2 * SIGMA * SIGMA));

            // 3. R(fi) - Feedback Reward ermitteln
            double intensity = feedback.intensity();
            double feedbackReward = feedback.isPositiveTrend() ? intensity : (1.0 - intensity) * 0.2;

            // 4. wi - Gesamtgewichtung für diesen historischen Beitrag
            double wi = gaussianKernel * feedbackReward;

            weightedSum += wi * attributeExtractor.applyAsDouble(xi);
            totalWeight += wi;
        }

        if (totalWeight == 0) return 1.0;

        // 5. y_hat - Der lokal gewichtete, geschätzte Zielwert
        double yHat = weightedSum / totalWeight;

        // 6. Faktor_a = y_hat / x.a
        return yHat / Math.max(0.01, currentValue);
    }

    private double calculateDistance(Track x, Track xi) {
        double sum = 0;
        sum += Math.pow(x.energy() - xi.energy(), 2);
        sum += Math.pow((x.bpm() - xi.bpm()) / 200.0, 2); // BPM-Normalisierung
        sum += Math.pow(x.danceability() - xi.danceability(), 2);
        sum += Math.pow(x.acousticness() - xi.acousticness(), 2);
        sum += Math.pow(x.instrumentalness() - xi.instrumentalness(), 2);
        sum += Math.pow(x.speechiness() - xi.speechiness(), 2);
        return Math.sqrt(sum);
    }
}
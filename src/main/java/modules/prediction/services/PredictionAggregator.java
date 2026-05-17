package modules.prediction.services;

import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.vision.structures.FeedbackResult;
import modules.prediction.structures.PredictedAttributes;
import modules.prediction.structures.PredictionFactor;

import java.util.List;

public class PredictionAggregator {
    private final List<PredictionStrategy> strategies;

    // Dependency Injection: Die Strategien werden von außen übergeben
    public PredictionAggregator(List<PredictionStrategy> strategies) {
        this.strategies = strategies;
    }

    public PredictedAttributes calculateNextAttributes(Track currentSong, FeedbackResult feedback) {
        double weightedEnergySum = 0.0;
        double weightedBpmSum = 0.0;
        double totalWeight = 0.0;
        System.out.println("Prediction-Aggregator (Starte Berechnung): Daten aus letztem Track -> Energy " + currentSong.energy() + " und BPM " + currentSong.bpm());

        // Iteriere über alle aktiven Strategien
        for (PredictionStrategy strategy : strategies) {
            PredictionFactor factor = strategy.calculate(currentSong);
            double weight = Math.max(0.0, strategy.getWeight());

            weightedEnergySum += factor.energyMultiplier() * weight;
            weightedBpmSum += factor.bpmMultiplier() * weight;
            totalWeight += weight;
        }

        PredictionFactor generalFactor = totalWeight > 0
                ? new PredictionFactor(
                clamp01(weightedEnergySum / totalWeight),
                clamp01(weightedBpmSum / totalWeight)
        )
                : new PredictionFactor(0.0, 0.0);

        double finalEnergy = clamp01(currentSong.energy() + (currentSong.energy() * generalFactor.energyMultiplier()));
        double finalBpm = currentSong.bpm() + (currentSong.bpm() * generalFactor.bpmMultiplier());

        // TODO Kamera-Feedback muss noch in Berechnung einbezogen werden

        return new PredictedAttributes(finalEnergy, finalBpm);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

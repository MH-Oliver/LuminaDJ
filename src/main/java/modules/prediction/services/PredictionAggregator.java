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
        double finalEnergy = currentSong.energy();
        double finalBpm = currentSong.bpm();
        System.out.println("Prediction-Aggregator (Starte Berechnung): Daten aus letztem Track -> Energy " + finalEnergy + " und BPM " + finalBpm);

        // Iteriere über alle aktiven Strategien
        for (PredictionStrategy strategy : strategies) {
            PredictionFactor factor = strategy.calculate(currentSong);
            double weight = strategy.getWeight();

            // Vektor-Multiplikation: (Basis * Faktor * Gewicht)
            finalEnergy += (currentSong.energy() * factor.energyMultiplier() * weight);
            finalBpm += (currentSong.bpm() * factor.bpmMultiplier() * weight);
        }

        // TODO Kamera-Feedback muss noch in Berechnung einbezogen werden

        return new PredictedAttributes(finalEnergy, finalBpm);
    }
}

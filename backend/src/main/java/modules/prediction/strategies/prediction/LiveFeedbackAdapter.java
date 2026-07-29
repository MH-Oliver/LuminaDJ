package modules.prediction.strategies.prediction;

import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictionFactor;
import modules.vision.structures.FeedbackResult;
import modules.music.structures.Track;

import java.util.HashMap;
import java.util.Map;

public class LiveFeedbackAdapter implements PredictionStrategy {

    private final FeedbackResult feedback;
    private final double weight;

    // Das Feedback und das Gewicht werden für diesen einen Durchlauf übergeben
    public LiveFeedbackAdapter(FeedbackResult feedback, double weight) {
        this.feedback = feedback;
        this.weight = weight;
    }

    @Override
    public double getWeight() {
        return this.weight;
    }

    /**
     * Berechnet den Anpassungsfaktor für den nächsten Song basierend auf dem Live-Kamera-Feedback.
     * <p>
     * <ul>
     * <li><b>Positiver Trend:</b> Das Momentum wird gehalten oder leicht gepusht. Ab einer sehr hohen Crowd-Intensität greift ein Limit (Faktor 1.0), um eine Endlos-Steigerung zu verhindern.</li>
     * <li><b>Negativer Trend (Reset):</b> Es wird ein kontextabhängiger, thematischer Bruch erzeugt:
     * <ul>
     * <li>Bei bisher <i>hoher</i> Energie: Bruch nach unten (Erholungsphase für eine erschöpfte Crowd).</li>
     * <li>Bei bisher <i>niedriger</i> Energie: Bruch nach oben (Wake-Up Call für eine gelangweilte Crowd).</li>
     * </ul>
     * </li>
     * </ul>
     */
    @Override
    public PredictionFactor calculate(Track currentTrack) {
        double currentIntensity = feedback.intensity();
        double currentEnergy = currentTrack.features().getOrDefault("energy", 0.5);

        Map<String, Double> multipliers = new HashMap<>();

        if (feedback.isPositiveTrend()) {
            double limitReached = currentIntensity > 0.80 ? 1.0 : 1.05;

            multipliers.put("energy", limitReached);
            multipliers.put("danceability", limitReached);
            multipliers.put("bpm", currentIntensity > 0.80 ? 1.0 : 1.02);
            multipliers.put("acousticness", 0.95);
            multipliers.put("instrumentalness", 1.0);
            multipliers.put("speechiness", 1.0);

        } else {
            if (currentEnergy > 0.70) {
                multipliers.put("energy", (currentIntensity < 0.40) ? 0.65 : 0.85);
                multipliers.put("bpm", 0.98);
                multipliers.put("danceability", 0.90);
                multipliers.put("acousticness", 1.30);
                multipliers.put("instrumentalness", 1.15);
                multipliers.put("speechiness", 1.0);

            } else {
                multipliers.put("energy", 1.40);
                multipliers.put("bpm", 1.10);
                multipliers.put("danceability", 1.30);
                multipliers.put("acousticness", 0.70);
                multipliers.put("instrumentalness", 0.80);
                multipliers.put("speechiness", 1.10);
            }
        }

        return new PredictionFactor(multipliers);
    }
}
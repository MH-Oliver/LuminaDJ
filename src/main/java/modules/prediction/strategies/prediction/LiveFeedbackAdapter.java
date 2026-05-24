package modules.prediction.strategies.prediction;

import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictionFactor;
import modules.vision.structures.FeedbackResult;
import modules.music.structures.Track;

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
        double currentEnergy = currentTrack.energy();

        if (feedback.isPositiveTrend()) {
            double energyPush = (currentIntensity > 80.0) ? 1.0 : 1.05;
            double bpmPush    = (currentIntensity > 80.0) ? 1.0 : 1.02;
            System.out.println("LiveFeedback: Crowd motiviert, halten der Stimmung");

            return new PredictionFactor(energyPush, bpmPush);

        } else {
            double energyBreak;
            double bpmBreak;

            if (currentEnergy > 0.70) {
                energyBreak = (currentIntensity < 40.0) ? 0.65 : 0.85;
                bpmBreak = 0.98;
                System.out.println("LiveFeedback: Crowd erschöpft -> Bruch nach UNTEN.");
            } else {
                energyBreak = 1.40;
                bpmBreak = 1.10;
                System.out.println("LiveFeedback: Crowd gelangweilt -> Bruch nach OBEN (Wake-Up Call!).");
            }

            return new PredictionFactor(energyBreak, bpmBreak);
        }
    }
}
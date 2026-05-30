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
            // Crowd ist motiviert -> Wir halten oder pushen das Momentum leicht.
            double limitReached = currentIntensity > 0.80 ? 1.0 : 1.05;

            double energyPush = limitReached;
            double dancePush  = limitReached;
            double bpmPush    = currentIntensity > 0.80 ? 1.0 : 1.02;

            // Stimmung ist top, also reduzieren wir Akustik leicht für mehr Club-Vibe
            double acousticPush = 0.95;
            double instrumentalPush = 1.0; // Instrumental / Vocals bleiben im aktuellen Flow
            double speechPush = 1.0;

            System.out.println("LiveFeedback: Crowd motiviert, halten der Stimmung");

            return new PredictionFactor(
                    energyPush, bpmPush, dancePush, acousticPush, instrumentalPush, speechPush
            );

        } else {
            // Trend ist negativ -> Wir müssen reagieren (Reset / Bruch)
            double energyBreak, bpmBreak, danceBreak, acousticBreak, instrumentalBreak, speechBreak;

            if (currentEnergy > 0.70) {
                System.out.println("LiveFeedback: Crowd erschöpft -> Bruch nach UNTEN.");

                energyBreak = (currentIntensity < 0.40) ? 0.65 : 0.85;
                bpmBreak = 0.98;
                danceBreak = 0.90; // Etwas den Groove rausnehmen
                acousticBreak = 1.30; // Deutlich mehr akustische, organische Sounds zur Erholung
                instrumentalBreak = 1.15; // Mehr Instrumentals, weniger anstrengende Vocals
                speechBreak = 1.0;

            } else {
                System.out.println("LiveFeedback: Crowd gelangweilt -> Bruch nach OBEN (Wake-Up Call!).");

                energyBreak = 1.40; // Harter Push
                bpmBreak = 1.10;
                danceBreak = 1.30; // Drastisch mehr Groove erzwingen
                acousticBreak = 0.70; // Harter Cut weg von chilliger Akustik, rein in elektronische Banger
                instrumentalBreak = 0.80; // Deutlich weniger Instrumental -> Wir brauchen Vocals zum Mitsingen!
                speechBreak = 1.10; // Evtl. ein paar Rap/Hype-Elemente reinbringen
            }

            return new PredictionFactor(
                    energyBreak, bpmBreak, danceBreak, acousticBreak, instrumentalBreak, speechBreak
            );
        }
    }
}
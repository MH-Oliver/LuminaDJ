package modules.prediction.services;

import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.strategies.prediction.LiveFeedbackAdapter;
import modules.vision.structures.FeedbackResult;
import modules.prediction.structures.PredictedAttributes;
import modules.prediction.structures.PredictionFactor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.lang.reflect.Constructor;

public class PredictionAggregator {
    private final List<PredictionStrategy> strategies;

    // Dependency Injection: Die Strategien werden von außen übergeben
    public PredictionAggregator(List<PredictionStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * Berechnet die neuen Attribute des nächsten Tracks.
     * <p>
     * Der Einfluss der Strategien wird auf Basis der Gewichte berechnet.
     * Am Ende wird der Durchschnitt des veränderten Wertes über alle Gewichte genommen.
     */

    public PredictedAttributes calculateNextAttributes(Track currentSong, FeedbackResult feedback) {
        double[] baseValues = new double[] {
                currentSong.energy(),
                currentSong.bpm(),
                currentSong.danceability(),
                currentSong.acousticness(),
                currentSong.instrumentalness(),
                currentSong.speechiness()
        };

        double[] finalValues = new double[baseValues.length];

        System.out.println("Prediction-Aggregator (Starte Berechnung): Daten aus letztem Track -> Energy "
                + baseValues[0] + " und BPM " + baseValues[1]);

        List<PredictionStrategy> runStrategies = new ArrayList<>(this.strategies);
        runStrategies.add(new LiveFeedbackAdapter(feedback, 0.8));

        // Iteriere über alle aktiven Strategien
        for (PredictionStrategy strategy : runStrategies) {
            PredictionFactor factor = strategy.calculate(currentSong);
            double weight = strategy.getWeight();

            int index = 0;

            // Die for-each Schleife läuft dynamisch über alle Attribute des PredictionFactors
            for (double factorValue : factor) {
                // Vektor-Multiplikation für das jeweilige Attribut: (Basis + [|(Basis*Faktor-120)| * Gewicht])
                // --> Wenn Gewicht 1.0, wird neuer Zielwert für Durchschnitts-Berechnung übernommen
                // --> Wenn Gewicht 0.0, wird alter Wert für Durchschnittsberechnung genommen
                var baseValue = baseValues[index];
                finalValues[index] += baseValue + ((baseValue * factorValue - baseValue) * weight);
                index++;
            }
        }

        // Durchschnitt berechnen
        int count = runStrategies.size();
        Arrays.setAll(finalValues, i -> finalValues[i] / count);

        // Dynamisches Erzeugen des Rückgabe-Objekts (PredictedAttributes) via Reflection
        try {
            Class<?>[] paramTypes = new Class[finalValues.length];
            Arrays.fill(paramTypes, double.class);

            // Primitive double-Werte in ein Object-Array packen für den Konstruktor
            Object[] constructorArgs = new Object[finalValues.length];
            for (int i = 0; i < finalValues.length; i++) {
                constructorArgs[i] = finalValues[i];
            }

            Constructor<PredictedAttributes> constructor = PredictedAttributes.class.getDeclaredConstructor(paramTypes);
            return constructor.newInstance(constructorArgs);

        } catch (Exception e) {
            throw new RuntimeException("Fehler beim dynamischen Erzeugen der PredictedAttributes", e);
        }
    }
}

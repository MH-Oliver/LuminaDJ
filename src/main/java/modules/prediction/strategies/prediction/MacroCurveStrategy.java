package modules.prediction.strategies.prediction;

import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictionFactor;
import modules.userContext.services.UserContextService;
import modules.userContext.structures.MacroCurve;

import java.time.LocalTime;
import java.util.Map;

public class MacroCurveStrategy implements PredictionStrategy {
    @Override
    public double getWeight() {
        return 0.7;
    }

    @Override
    public PredictionFactor calculate(Track currentTrack) {
        var context = UserContextService.getInstance().getCurrentContext();
        LocalTime currentTime = context.currentTime();
        Map<String, MacroCurve> curves = context.attributeCurves();

        var predictionFactor = new PredictionFactor(
                getFactor("energy", currentTrack.energy(), curves, currentTime),
                getFactor("bpm", currentTrack.bpm(), curves, currentTime),
                getFactor("danceability", currentTrack.danceability(), curves, currentTime),
                getFactor("acousticness", currentTrack.acousticness(), curves, currentTime),
                getFactor("instrumentalness", currentTrack.instrumentalness(), curves, currentTime),
                getFactor("speechiness", currentTrack.speechiness(), curves, currentTime)
        );

        System.out.println("Macro-Curve: " + predictionFactor);

        return predictionFactor;
    }

    /**
     * Hilfsmethode: Prüft ob eine Kurve für das Attribut existiert und berechnet den Faktor.
     * Existiert keine Kurve, wird 1.0 (keine Veränderung) zurückgegeben.
     */
    private double getFactor(String curveKey, double currentValue, Map<String, MacroCurve> curves, LocalTime time) {
        if (curves != null && curves.containsKey(curveKey)) {
            double targetValue = curves.get(curveKey).getTargetValueAt(time);
            return targetValue / Math.max(0.01, currentValue); // Teiler durch 0 verhindern
        }
        return 1.0;
    }
}
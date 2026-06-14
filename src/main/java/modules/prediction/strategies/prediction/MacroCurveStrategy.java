package modules.prediction.strategies.prediction;

import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictionFactor;
import modules.userContext.services.UserContextService;
import modules.userContext.structures.MacroCurve;

import java.time.LocalTime;
import java.util.HashMap;
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

        Map<String, Double> multipliers = new HashMap<>();

        for (String key : currentTrack.features().keySet()) {
            double currentValue = currentTrack.features().get(key);
            multipliers.put(key, getFactor(key, currentValue, curves, currentTime));
        }

        return new PredictionFactor(multipliers);
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
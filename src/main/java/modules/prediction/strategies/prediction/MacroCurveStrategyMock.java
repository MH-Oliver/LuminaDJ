package modules.prediction.strategies.prediction;

import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictionFactor;

public class MacroCurveStrategyMock implements PredictionStrategy {
    @Override
    public double getWeight() { return 0.6; } // Hohes Gewicht für den Plan

    @Override
    public PredictionFactor calculate(Track currentTrack) {
        var predictionFactor = new PredictionFactor(1.05, 1.02, 1.10, 0.9, 1.0, 1.0);
        System.out.println("Macro-Curve-Strategy: track \"" + currentTrack.name() + "\", " + predictionFactor);
        return predictionFactor;
    }
}

package modules.prediction.strategies.prediction;

import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictionFactor;

public class MacroCurveStrategyMock implements PredictionStrategy {
    @Override
    public double getWeight() { return 0.6; } // Hohes Gewicht für den Plan

    @Override
    public PredictionFactor calculate(Track currentTrack) {
        System.out.println("Marcro-Curve-Strategy: track \"" + currentTrack.name() + "\", die Energy und BPM muss stark erhöht werden");
        return new PredictionFactor(0.10, 0.05);
    }
}

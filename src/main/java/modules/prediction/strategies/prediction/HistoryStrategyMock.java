package modules.prediction.strategies.prediction;

import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictionFactor;

public class HistoryStrategyMock implements PredictionStrategy {
    @Override
    public double getWeight() { return 0.2; }

    @Override
    public PredictionFactor calculate(Track currentTrack) {
        var predictionFactor = new PredictionFactor(1.04, 1.02);
        System.out.println("History-Strategy: track \"" + currentTrack.name() + "\", " + predictionFactor);
        return predictionFactor;
    }
}
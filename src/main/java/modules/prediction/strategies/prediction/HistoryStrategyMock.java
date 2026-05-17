package modules.prediction.strategies.prediction;

import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictionFactor;

public class HistoryStrategyMock implements PredictionStrategy {
    @Override
    public double getWeight() { return 0.25; }

    @Override
    public PredictionFactor calculate(Track currentTrack) {
        System.out.println("History-Strategy: track \"" + currentTrack.name() + "\", die Energy und BPM muss etwas erhöht werden");
        return new PredictionFactor(0.70, 0.6);
    }
}
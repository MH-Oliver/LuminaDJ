package modules.prediction.strategies.core;

import modules.music.structures.Track;
import modules.prediction.structures.PredictionFactor;

/**
 * Interface für alle Auswertungs-Module (Macro, History, Live)
 */
public interface PredictionStrategy {
    double getWeight();
    PredictionFactor calculate(Track currentTrack);
}
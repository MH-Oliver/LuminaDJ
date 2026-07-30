package modules.music.strategies.core;

import modules.music.structures.Track;
import modules.prediction.structures.PredictedAttributes;

/**
 * Interface für die Song-Beschaffung (Graph oder API)
 */
public interface MusicSourceAdapter {
    Track getNextSong(PredictedAttributes predictedTarget, Track currentSong);
}

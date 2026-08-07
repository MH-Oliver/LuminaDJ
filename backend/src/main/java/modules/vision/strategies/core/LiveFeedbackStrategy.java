package modules.vision.strategies.core;

import modules.music.structures.Track;
import modules.vision.structures.FeedbackResult;
import org.springframework.stereotype.Service;

/**
 * Interface für die parallele Kamera-Auswertung
 */
public interface LiveFeedbackStrategy {
    void startParallelEvaluation(Track song);
    FeedbackResult stopAndGetResult();
}

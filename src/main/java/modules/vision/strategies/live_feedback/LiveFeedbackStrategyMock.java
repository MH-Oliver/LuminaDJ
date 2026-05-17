package modules.vision.strategies.live_feedback;

import modules.music.structures.Track;
import modules.vision.structures.FeedbackResult;
import modules.vision.strategies.core.LiveFeedbackStrategy;

public class LiveFeedbackStrategyMock implements LiveFeedbackStrategy {

    private volatile boolean isRunning = false;
    private Thread evaluationThread;

    @Override
    public void startParallelEvaluation(Track song) {
        isRunning = true;

        evaluationThread = new Thread(() -> {
            System.out.println("Mock [Live-Kamera]: Starte parallele Kamera-Auswertung für " + song.name());

            while (isRunning) {
                try {
                    Thread.sleep(3000);

                    if (isRunning) {
                        System.out.println("Mock [Live-Kamera]: Stimmung ist weiterhin gut...");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        evaluationThread.start();
    }

    @Override
    public FeedbackResult stopAndGetResult() {
        isRunning = false;

        if (evaluationThread != null) {
            evaluationThread.interrupt();
        }

        double intensity = 0.8;
        System.out.println("Mock [Live-Kamera]: Song beendet. Stoppe Auswertung und liefere finales Feedback (intensity=" + intensity + ")." );
        return new FeedbackResult(true, intensity);
    }
}

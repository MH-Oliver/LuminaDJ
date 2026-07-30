package modules.core;

import modules.music.repositories.PlayedSongRepository;
import modules.music.repositories.SessionHistoryRepository;
import modules.music.strategies.core.MusicPlayerAdapter;
import modules.music.strategies.core.MusicSourceAdapter;
import modules.music.structures.Track;
import modules.prediction.services.PredictionAggregator;
import modules.vision.structures.FeedbackResult;
import modules.prediction.structures.PredictedAttributes;
import modules.vision.strategies.core.LiveFeedbackStrategy;

public class DjSessionController {

    private final MusicPlayerAdapter player;
    private final LiveFeedbackStrategy liveFeedback;
    private final PredictionAggregator aggregator;
    private final MusicSourceAdapter sourceAdapter;
    private final SessionHistoryRepository history;
    private final PlayedSongRepository playedSongRepo;

    private boolean sessionActive = true;

    // Dependency Injection über den Konstruktor
    public DjSessionController(
            MusicPlayerAdapter player,
            LiveFeedbackStrategy liveFeedback,
            PredictionAggregator aggregator,
            MusicSourceAdapter sourceAdapter,
            SessionHistoryRepository history,
            PlayedSongRepository playedSongRepo) {
        this.player = player;
        this.liveFeedback = liveFeedback;
        this.aggregator = aggregator;
        this.sourceAdapter = sourceAdapter;
        this.history = history;
        this.playedSongRepo = playedSongRepo;
    }

    public void startSession(Track entrySong) {
        Track currentSong = entrySong;

        while (sessionActive) {
            // 1. ZUSTAND: Abspielen & parallel Beobachten (Fork)
            liveFeedback.startParallelEvaluation(currentSong);

            // Simuliert das Blockieren, bis der Song zu Ende ist
            try {
                player.play(currentSong);
            } catch (IllegalArgumentException exception) {
                liveFeedback.stopAndGetResult();
                System.err.println("Player wirft Fehler: " + exception);
                return;
            }

            // 2. TRIGGER: Song beendet -> Ergebnisse einsammeln
            FeedbackResult feedback = liveFeedback.stopAndGetResult();

            history.addEntry(currentSong, feedback);

            // 3. AUSWERTUNG: Aggregator verrechnet alle Parameter
            PredictedAttributes predictedTarget = aggregator.calculateNextAttributes(currentSong, feedback);

            System.out.println("General Predicted Target: " + predictedTarget);
            // 4. NEUEN SONG FINDEN: Über Graph oder API
            Track nextSong = sourceAdapter.getNextSong(predictedTarget, currentSong);

            System.out.println("DJSessionController | Gefundener Song: " + nextSong);

            playedSongRepo.markAsPlayed(nextSong.id());

            currentSong = nextSong;
        }
    }
}

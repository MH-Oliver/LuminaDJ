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
import org.springframework.stereotype.Service;

@Service
public class DjSessionController {

    private final MusicPlayerAdapter player;
    private final LiveFeedbackStrategy liveFeedback;
    private final PredictionAggregator aggregator;
    private final MusicSourceAdapter sourceAdapter;
    private final SessionHistoryRepository history;
    private final PlayedSongRepository playedSongRepo;

    private boolean sessionActive = true;

    // NEU: Hält den aktuell laufenden Track
    private Track currentTrack;

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

    // NEU: Getter für die API
    public Track getCurrentTrack() {
        return this.currentTrack;
    }

    public MusicPlayerAdapter getPlayer() { return player; }
    public LiveFeedbackStrategy getLiveFeedback() { return liveFeedback; }
    public PredictionAggregator getAggregator() { return aggregator; }
    public MusicSourceAdapter getSourceAdapter() { return sourceAdapter; }
    public SessionHistoryRepository getHistory() { return history; }
    public PlayedSongRepository getPlayedSongRepo() { return playedSongRepo; }

    public void startSession(Track entrySong) {
        Track currentSong = entrySong;

        while (sessionActive) {
            // NEU: Track-Zustand für das Frontend / die API speichern
            this.currentTrack = currentSong;

            liveFeedback.startParallelEvaluation(currentSong);

            try {
                player.play(currentSong);
            } catch (IllegalArgumentException exception) {
                liveFeedback.stopAndGetResult();
                System.err.println("Player wirft Fehler: " + exception);
                return;
            }

            FeedbackResult feedback = liveFeedback.stopAndGetResult();
            history.addEntry(currentSong, feedback);

            PredictedAttributes predictedTarget = aggregator.calculateNextAttributes(currentSong, feedback);
            System.out.println("General Predicted Target: " + predictedTarget);

            Track nextSong = sourceAdapter.getNextSong(predictedTarget, currentSong);
            System.out.println("DJSessionController | Gefundener Song: " + nextSong);

            playedSongRepo.markAsPlayed(nextSong.id());
            currentSong = nextSong;
        }
    }
}
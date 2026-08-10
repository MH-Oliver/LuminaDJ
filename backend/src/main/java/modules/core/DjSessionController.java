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
import modules.userContext.structures.UserContextDTO;


public class DjSessionController {
    private final MusicPlayerAdapter player;
    private final LiveFeedbackStrategy liveFeedback;
    private final PredictionAggregator aggregator;
    private final MusicSourceAdapter sourceAdapter;
    private final SessionHistoryRepository history;
    private final PlayedSongRepository playedSongRepo;

    private final UserContextDTO context; // NEU: Speichert die Timeline
    private boolean sessionActive = true;
    private Track currentTrack;

    // Konstruktor um den Parameter 'context' erweitern
    public DjSessionController(
            MusicPlayerAdapter player,
            LiveFeedbackStrategy liveFeedback,
            PredictionAggregator aggregator,
            MusicSourceAdapter sourceAdapter,
            SessionHistoryRepository history,
            PlayedSongRepository playedSongRepo,
            UserContextDTO context) {
        this.player = player;
        this.liveFeedback = liveFeedback;
        this.aggregator = aggregator;
        this.sourceAdapter = sourceAdapter;
        this.history = history;
        this.playedSongRepo = playedSongRepo;
        this.context = context;
    }

    public UserContextDTO getContext() { return context; } // Getter für das Frontend
    public Track getCurrentTrack() { return this.currentTrack; }
    public MusicPlayerAdapter getPlayer() { return player; }
    public LiveFeedbackStrategy getLiveFeedback() { return liveFeedback; }
    public PredictionAggregator getAggregator() { return aggregator; }
    public MusicSourceAdapter getSourceAdapter() { return sourceAdapter; }
    public SessionHistoryRepository getHistory() { return history; }
    public PlayedSongRepository getPlayedSongRepo() { return playedSongRepo; }

    public void startSession(Track entrySong) {
        Track currentSong = entrySong;
        while (sessionActive) {
            this.currentTrack = currentSong;
            liveFeedback.startParallelEvaluation(currentSong);
            try {
                player.play(currentSong);
            } catch (IllegalArgumentException exception) {
                liveFeedback.stopAndGetResult();
                System.err.println("Player wirft Fehler: " + exception);
                return;
            }

            // WICHTIG: Prüfen, ob in der Zwischenzeit Edit/Cancel gedrückt wurde
            if (!sessionActive) {
                liveFeedback.stopAndGetResult();
                break;
            }

            FeedbackResult feedback = liveFeedback.stopAndGetResult();
            history.addEntry(currentSong, feedback);
            PredictedAttributes predictedTarget = aggregator.calculateNextAttributes(currentSong, feedback);
            Track nextSong = sourceAdapter.getNextSong(predictedTarget, currentSong);
            playedSongRepo.markAsPlayed(nextSong.id());
            currentSong = nextSong;
        }
    }

    // NEU: Bricht die Session sauber ab
    public void stopSession() {
        this.sessionActive = false;
        this.player.stop();
    }
}

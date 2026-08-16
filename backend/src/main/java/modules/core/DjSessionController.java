package modules.core;

import modules.music.repositories.PlayedSongRepository;
import modules.music.repositories.SessionHistoryRepository;
import modules.music.strategies.core.MusicPlayerAdapter;
import modules.music.strategies.core.MusicSourceAdapter;
import modules.music.structures.Track;
import modules.prediction.services.PredictionAggregator;
import modules.prediction.strategies.prediction.PrioritizeStrategy;
import modules.prediction.structures.PredictedAttributes;
import modules.userContext.structures.UserContextDTO;

public class DjSessionController {
    private final MusicPlayerAdapter player;
    private final PredictionAggregator aggregator;
    private final MusicSourceAdapter sourceAdapter;
    private final SessionHistoryRepository history;
    private final PlayedSongRepository playedSongRepo;
    private final PrioritizeStrategy prioritizeStrategy;
    private UserContextDTO context;
    private boolean sessionActive = true;
    private Track currentTrack;

    public DjSessionController(
            MusicPlayerAdapter player,
            PredictionAggregator aggregator,
            MusicSourceAdapter sourceAdapter,
            SessionHistoryRepository history,
            PlayedSongRepository playedSongRepo,
            UserContextDTO context,
            PrioritizeStrategy prioritizeStrategy) {

        this.player = player;
        this.aggregator = aggregator;
        this.sourceAdapter = sourceAdapter;
        this.history = history;
        this.playedSongRepo = playedSongRepo;
        this.context = context;
        this.prioritizeStrategy = prioritizeStrategy;
    }

    public UserContextDTO getContext() { return context; }
    public void setContext(UserContextDTO context) { this.context = context; }
    public Track getCurrentTrack() { return this.currentTrack; }
    public MusicPlayerAdapter getPlayer() { return player; }
    public PredictionAggregator getAggregator() { return aggregator; }
    public MusicSourceAdapter getSourceAdapter() { return sourceAdapter; }
    public SessionHistoryRepository getHistory() { return history; }
    public PlayedSongRepository getPlayedSongRepo() { return playedSongRepo; }

    public void startSession(Track entrySong) {
        Track currentSong = entrySong;

        while (sessionActive) {
            this.currentTrack = currentSong;

            try {
                player.play(currentSong);
            } catch (IllegalArgumentException exception) {
                System.err.println("Player wirft Fehler: " + exception);
                return;
            }

            if (!sessionActive) {
                break;
            }

            history.addEntry(currentSong);

            PredictedAttributes predictedTarget = aggregator.calculateNextAttributes(currentSong);

            Track nextSong = sourceAdapter.getNextSong(predictedTarget, currentSong);
            playedSongRepo.markAsPlayed(nextSong.id());
            currentSong = nextSong;
        }
    }

    public void prioritizeCurrentTrack() {
        if (currentTrack != null && prioritizeStrategy != null) {
            prioritizeStrategy.addTrack(currentTrack);
        }
    }

    public void stopSession() {
        this.sessionActive = false;
        this.player.stop();
    }
}
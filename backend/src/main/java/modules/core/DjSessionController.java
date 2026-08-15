package modules.core;

import modules.music.repositories.PlayedSongRepository;
import modules.music.repositories.SessionHistoryRepository;
import modules.music.strategies.core.MusicPlayerAdapter;
import modules.music.strategies.core.MusicSourceAdapter;
import modules.music.structures.Track;
import modules.prediction.services.PredictionAggregator;
import modules.prediction.structures.PredictedAttributes;
import modules.userContext.structures.UserContextDTO;

public class DjSessionController {
    private final MusicPlayerAdapter player;
    private final PredictionAggregator aggregator;
    private final MusicSourceAdapter sourceAdapter;
    private final SessionHistoryRepository history;
    private final PlayedSongRepository playedSongRepo;
    private final UserContextDTO context;
    private boolean sessionActive = true;
    private Track currentTrack;

    // Konstruktor ohne LiveFeedbackStrategy
    public DjSessionController(
            MusicPlayerAdapter player,
            PredictionAggregator aggregator,
            MusicSourceAdapter sourceAdapter,
            SessionHistoryRepository history,
            PlayedSongRepository playedSongRepo,
            UserContextDTO context) {
        this.player = player;
        this.aggregator = aggregator;
        this.sourceAdapter = sourceAdapter;
        this.history = history;
        this.playedSongRepo = playedSongRepo;
        this.context = context;
    }

    public UserContextDTO getContext() { return context; }
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

            // Kein Live-Feedback mehr abwarten, Song direkt in die Historie speichern
            history.addEntry(currentSong);

            // Neue Song-Eigenschaften vorhersagen (ohne Feedback)
            PredictedAttributes predictedTarget = aggregator.calculateNextAttributes(currentSong);

            Track nextSong = sourceAdapter.getNextSong(predictedTarget, currentSong);
            playedSongRepo.markAsPlayed(nextSong.id());
            currentSong = nextSong;
        }
    }

    public void stopSession() {
        this.sessionActive = false;
        this.player.stop();
    }
}
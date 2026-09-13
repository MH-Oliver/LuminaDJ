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

/**
 * Die zentrale Kontrollinstanz (Facade) für eine laufende DJ-Session.
 *
 * Diese Klasse orchestriert das Zusammenspiel der wichtigsten Komponenten:
 * - Dem Musik-Player (spielt den Song über Spotify ab)
 * - Der Vorhersage-KI (Aggregator berechnet, wie der nächste Song klingen soll)
 * - Dem Datenbank-Adapter (Sucht den besten nächsten Song)
 * - Der Song-Historie (Speichert, was gespielt wurde, um Wiederholungen zu vermeiden)
 */
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

    /**
     * Startet die Haupt-Schleife der DJ-Session.
     * Spielt den übergebenen Start-Song ab, berechnet währenddessen den nächsten Song
     * und lädt diesen nahtlos nach. Endet erst, wenn die Session gestoppt wird.
     *
     * @param entrySong Der allererste Song, mit dem die Session gestartet werden soll.
     */
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

            // 1. Berechne die Audio-Eigenschaften, die der nächste Song haben sollte
            PredictedAttributes predictedTarget = aggregator.calculateNextAttributes(currentSong);

            // 2. Suche in der Datenbank (bzw. auf Spotify) nach dem besten Treffer
            Track nextSong = sourceAdapter.getNextSong(predictedTarget, currentSong);

            // 3. Markiere ihn als gespielt
            playedSongRepo.markAsPlayed(nextSong.id());
            currentSong = nextSong;
        }
    }

    /**
     * Teilt der KI mit, dass der Vibe des aktuellen Songs stark bevorzugt werden soll.
     * Nutzt die PrioritizeStrategy, um zukünftige Lieder ähnlicher zu machen.
     */
    public void prioritizeCurrentTrack() {
        if (currentTrack != null && prioritizeStrategy != null) {
            prioritizeStrategy.addTrack(currentTrack);
        }
    }

    /**
     * Beendet die aktive DJ-Schleife und stoppt den Musikplayer.
     */
    public void stopSession() {
        this.sessionActive = false;
        this.player.stop();
    }
}
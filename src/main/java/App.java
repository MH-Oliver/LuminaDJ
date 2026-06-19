import modules.core.DjSessionController;
import modules.music.repositories.SessionHistoryRepository;
import modules.music.services.SessionBootstrapper;
import modules.music.strategies.music_player.spotify.SpotifyAdapter;
import modules.music.strategies.music_source.HybridSourceAdapter;
import modules.music.strategies.music_source.LocalSongDatabaseAdapter;
import modules.music.strategies.music_source.SpotifySourceAdapter;
import modules.music.structures.Genre;
import modules.music.structures.Track;
import modules.prediction.services.PredictionAggregator;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.strategies.prediction.HistoryStrategy;
import modules.prediction.strategies.prediction.MacroCurveStrategy;
import modules.userContext.strategies.impl.UserContextStrategyMock;
import modules.vision.strategies.live_feedback.LiveFeedbackStrategyMock;

import java.util.List;
import java.util.Map;

public class App
{
    public static void main( String[] args ) {
        var localSongDatabaseAdapter = new LocalSongDatabaseAdapter();

        DjSessionController controller = getDjSessionController(localSongDatabaseAdapter);

        var sessionBootstrapper = new SessionBootstrapper(localSongDatabaseAdapter);
        Map<Genre, Double> mixedGenre = Map.of(
                Genre.ROCK, 1.0
        );

        Track entrySong = sessionBootstrapper.generateFirstTrack(mixedGenre);
        System.out.println("Gefundener Entry Song: " + entrySong);
        controller.startSession(entrySong);
    }

    private static DjSessionController getDjSessionController(LocalSongDatabaseAdapter localSongDatabaseAdapter) {
        var userContextStrategy = new UserContextStrategyMock();

        var playerMock = new SpotifyAdapter();
        var liveFeedbackMock = new LiveFeedbackStrategyMock();

        var history = new SessionHistoryRepository();

        var spotifyApiAdapter = new SpotifySourceAdapter();

        var hybridAdapter = new HybridSourceAdapter(localSongDatabaseAdapter, spotifyApiAdapter);

        List<PredictionStrategy> strategies = List.of(
                new MacroCurveStrategy(localSongDatabaseAdapter, userContextStrategy),
                new HistoryStrategy(history)
        );
        var aggregator = new PredictionAggregator(strategies);

        return new DjSessionController(
                playerMock, liveFeedbackMock, aggregator, hybridAdapter, history
        );
    }
}
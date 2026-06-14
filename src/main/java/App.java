import modules.core.DjSessionController;
import modules.music.repositories.SessionHistoryRepository;
import modules.music.strategies.music_player.spotify.SpotifyAdapter;
import modules.music.strategies.music_source.HybridSourceAdapter;
import modules.music.strategies.music_source.LocalSongDatabaseAdapter;
import modules.music.strategies.music_source.SpotifySourceAdapter;
import modules.music.structures.Track;
import modules.prediction.services.PredictionAggregator;
import modules.prediction.strategies.prediction.HistoryStrategy;
import modules.prediction.strategies.prediction.MacroCurveStrategy;
import modules.userContext.services.UserContextService;
import modules.userContext.strategies.impl.UserContextStrategyMock;
import modules.vision.strategies.live_feedback.LiveFeedbackStrategyMock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class App
{
    public static void main( String[] args ) {
        DjSessionController controller = getDjSessionController();

        Map<String, Double> entryFeatures = new HashMap<>();
        entryFeatures.put("energy", 0.60);
        entryFeatures.put("bpm", 110.0);
        entryFeatures.put("danceability", 0.55);
        entryFeatures.put("acousticness", 0.40);
        entryFeatures.put("instrumentalness", 0.00);
        entryFeatures.put("speechiness", 0.05);

        Track entrySong = new Track("3K4HG9evC7dg3N0R9cYqk4", "One Step Closer", "Linkin Park", entryFeatures);
        controller.startSession(entrySong);
    }

    private static DjSessionController getDjSessionController() {
        var playerMock = new SpotifyAdapter();
        var liveFeedbackMock = new LiveFeedbackStrategyMock();

        var history = new SessionHistoryRepository();

        UserContextService.getInstance().setStrategy(new UserContextStrategyMock());

        var spotifyApiAdapter = new SpotifySourceAdapter();

        var localKnnAdapter = new LocalSongDatabaseAdapter();

        var hybridAdapter = new HybridSourceAdapter(localKnnAdapter, spotifyApiAdapter);

        var strategies = List.of(
                new MacroCurveStrategy(),
                new HistoryStrategy(history)
        );
        var aggregator = new PredictionAggregator(strategies);

        DjSessionController controller = new DjSessionController(
                playerMock, liveFeedbackMock, aggregator, hybridAdapter, history
        );
        return controller;
    }
}
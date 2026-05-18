import modules.core.DjSessionController;
import modules.music.strategies.music_player.MusicPlayerAdapterMock;
import modules.music.strategies.music_source.MusicGraphAdapterMock;
import modules.music.structures.Track;
import modules.prediction.services.PredictionAggregator;
import modules.prediction.strategies.prediction.HistoryStrategyMock;
import modules.prediction.strategies.prediction.MacroCurveStrategyMock;
import modules.vision.strategies.live_feedback.LiveFeedbackStrategyMock;

import java.util.List;

public class App
{
    public static void main( String[] args ) {
        var playerMock = new MusicPlayerAdapterMock();
        var liveFeedbackMock = new LiveFeedbackStrategyMock();
        var graphAdapterMock = new MusicGraphAdapterMock();

        var strategies = List.of(
                new MacroCurveStrategyMock(),
                new HistoryStrategyMock()
        );
        var aggregator = new PredictionAggregator(strategies);

        DjSessionController controller = new DjSessionController(
                playerMock, liveFeedbackMock, aggregator, graphAdapterMock
        );

        Track entrySong = new Track("3K4HG9evC7dg3N0R9cYqk4", "One Step Closer", "Linkin Park", 0.6, 120.0);
        controller.startSession(entrySong);
    }
}
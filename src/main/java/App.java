import modules.core.DjSessionController;
import modules.music.repositories.SessionHistoryRepository;
import modules.music.strategies.music_player.MusicPlayerAdapterMock;
import modules.music.strategies.music_source.ReccoBeatsAdapter;
import modules.music.structures.Track;
import modules.prediction.services.PredictionAggregator;
import modules.prediction.strategies.prediction.HistoryStrategy;
import modules.prediction.strategies.prediction.MacroCurveStrategy;
import modules.userContext.services.UserContextService;
import modules.userContext.strategies.impl.UserContextStrategyMock;
import modules.vision.strategies.detection.DetectionStrategyLangChain4j;
import modules.vision.strategies.live_feedback.SmartphoneKameraStrategy;

import java.util.List;

public class App
{
    public static void main( String[] args ) {
        var playerMock = new MusicPlayerAdapterMock();
        var liveFeedbackMock = new SmartphoneKameraStrategy(
                new DetectionStrategyLangChain4j()
        );

        var history = new SessionHistoryRepository();

        UserContextService.getInstance().setStrategy(new UserContextStrategyMock());

        var graphAdapterMock = new ReccoBeatsAdapter();

        var strategies = List.of(
                new MacroCurveStrategy(),
                new HistoryStrategy(history)
        );
        var aggregator = new PredictionAggregator(strategies);

        DjSessionController controller = new DjSessionController(
                playerMock, liveFeedbackMock, aggregator, graphAdapterMock, history
        );

        Track entrySong = new Track("3K4HG9evC7dg3N0R9cYqk4", "One Step Closer", "Linkin Park", 0.6, 120.0, 0.4, 0.1, 0.8, 0.05);
        controller.startSession(entrySong);
    }
}
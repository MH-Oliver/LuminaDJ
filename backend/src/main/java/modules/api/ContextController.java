// modules/api/ContextController.java
package modules.api;

import com.fasterxml.jackson.databind.JsonNode;
import modules.core.DjSessionController;
import modules.music.repositories.PlayedSongRepository;
import modules.music.repositories.SessionHistoryRepository;
import modules.music.services.SessionBootstrapper;
import modules.music.strategies.core.MusicPlayerAdapter;
import modules.music.strategies.core.MusicSourceAdapter;
import modules.music.strategies.music_player.MusicPlayerAdapterMock;
import modules.music.strategies.music_player.spotify.SpotifyAdapter;
import modules.music.strategies.music_player.spotify.SpotifyAuthenticator;
import modules.music.strategies.music_source.HybridSourceAdapter;
import modules.music.strategies.music_source.LocalSongDatabaseAdapter;
import modules.music.strategies.music_source.SpotifySourceAdapter;
import modules.music.structures.Genre;
import modules.music.structures.Track;
import modules.prediction.services.PredictionAggregator;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.strategies.prediction.HistoryStrategy;
import modules.prediction.strategies.prediction.MacroCurveStrategy;
import modules.userContext.strategies.core.UserContextStrategy;
import modules.vision.strategies.core.LiveFeedbackStrategy;
import modules.vision.strategies.live_feedback.LiveFeedbackStrategyMock;
import modules.vision.strategies.live_feedback.SmartphoneKameraStrategy;
import modules.vision.strategies.detection.DetectionStrategyMock;
import modules.userContext.structures.GenreTimeline;
import modules.userContext.structures.Location;
import modules.userContext.structures.TimelinePhase;
import modules.userContext.structures.UserContextDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ContextController {

    private final ActiveSessionService sessionService;
    private final SpotifyAuthenticator authenticator; // NEU

    // Spring boot injiziert uns hier automatisch den ActiveSessionService und den Authenticator
    public ContextController(ActiveSessionService sessionService, SpotifyAuthenticator authenticator) {
        this.sessionService = sessionService;
        this.authenticator = authenticator;
    }

    @PostMapping("/context")
    public ResponseEntity<Map<String, String>> handleContext(@RequestBody JsonNode payload) {
        System.out.println("Endpoint /api/context wurde aufgerufen!");
        try {
            UserContextDTO context = mapUserContext(payload);
            new Thread(() -> startMusicSession(context)).start();
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid payload"));
        }
    }

    private void startMusicSession(UserContextDTO context) {
        System.out.println("Starte Musik-Session mit empfangenem Context...");
        UserContextStrategy userContextStrategy = () -> context;
        DjSessionController controller = buildDjSessionWithMocks(userContextStrategy);
        sessionService.setActiveSession(controller);

        Map<Genre, Double> startWeights = context.timeline().getWeightsAt(0.0);
        var localDb = new LocalSongDatabaseAdapter(controller.getPlayedSongRepo(), userContextStrategy);
        var sessionBootstrapper = new SessionBootstrapper(localDb);

        Track entrySong = sessionBootstrapper.generateFirstTrack(startWeights);
        controller.getPlayedSongRepo().markAsPlayed(entrySong.id());
        System.out.println("Gefundener Entry Song: " + entrySong);

        controller.startSession(entrySong);
    }

    private DjSessionController buildDjSessionWithMocks(UserContextStrategy contextStrategy) {
        var playedSongRepo = new PlayedSongRepository();
        var historyRepo = new SessionHistoryRepository();
        var localDb = new LocalSongDatabaseAdapter(playedSongRepo, contextStrategy);

        // =========================================
        // 1. PLAYER (Audio abspielen)
        // =========================================
        //MusicPlayerAdapter player = new MusicPlayerAdapterMock();

        // NEU: Übergabe des Authenticators, damit der Adapter die Spotify-API erhält
        MusicPlayerAdapter player = new SpotifyAdapter(this.authenticator);

        // =========================================
        // 2. LIVE-FEEDBACK (Kamera)
        // =========================================
        LiveFeedbackStrategy liveFeedback = new LiveFeedbackStrategyMock();
        // LiveFeedbackStrategy liveFeedback = new SmartphoneKameraStrategy(new DetectionStrategyMock());

        // =========================================
        // 3. MUSIC SOURCE (Woher kommen die Songs?)
        // =========================================
        MusicSourceAdapter sourceAdapter = localDb; // Mock: Nur lokaler CSV-Datensatz
        // MusicSourceAdapter sourceAdapter = new HybridSourceAdapter(localDb, new SpotifySourceAdapter());

        List<PredictionStrategy> strategies = List.of(
                new MacroCurveStrategy(localDb, contextStrategy),
                new HistoryStrategy(historyRepo)
        );
        var aggregator = new PredictionAggregator(strategies);

        return new DjSessionController(
                player, liveFeedback, aggregator, sourceAdapter, historyRepo, playedSongRepo, contextStrategy.getUserContext()
        );
    }

    private UserContextDTO mapUserContext(JsonNode payload) {
        int tempo = payload.path("tempo").asInt(120);
        String locationRaw = payload.path("location").asText("Bar");
        Location location = Location.valueOf(locationRaw);
        String startTimeRaw = payload.path("startTime").asText(LocalTime.now().withNano(0).toString());
        LocalTime startTime = LocalTime.parse(startTimeRaw);
        GenreTimeline timeline = mapTimeline(payload.path("timeline"));
        int cooldown = payload.path("songCooldownMinutes").asInt(30);
        int totalMinutes = payload.path("totalMinutes").asInt(120);

        return new UserContextDTO(tempo, location, startTime, timeline, cooldown, totalMinutes);
    }

    private GenreTimeline mapTimeline(JsonNode timelineNode) {
        JsonNode phasesNode = timelineNode.path("phases");
        List<TimelinePhase> phases = new ArrayList<>();
        if (phasesNode.isArray()) {
            for (JsonNode phaseNode : phasesNode) {
                String genreRaw = phaseNode.path("genre").asText("POP");
                Genre genre = Genre.valueOf(genreRaw);
                double duration = phaseNode.path("durationMinutes").asDouble(60.0);
                double transition = phaseNode.path("transitionOutMinutes").asDouble(5.0);
                phases.add(new TimelinePhase(genre, duration, transition));
            }
        }
        if (phases.isEmpty()) {
            phases.add(new TimelinePhase(Genre.POP, 60.0, 5.0));
        }
        return new GenreTimeline(phases);
    }

    @GetMapping("/context/current")
    public ResponseEntity<UserContextDTO> getCurrentContext() {
        DjSessionController sessionController = sessionService.getActiveSession();
        if (sessionController != null && sessionController.getContext() != null) {
            return ResponseEntity.ok(sessionController.getContext());
        }
        return ResponseEntity.notFound().build();
    }
}
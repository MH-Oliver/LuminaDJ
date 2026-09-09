package modules.api;

import com.fasterxml.jackson.databind.JsonNode;
import modules.core.DjSessionController;
import modules.music.repositories.PlayedSongRepository;
import modules.music.repositories.SessionHistoryRepository;
import modules.music.services.SessionBootstrapper;
import modules.music.strategies.core.MusicPlayerAdapter;
import modules.music.strategies.core.MusicSourceAdapter;
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
import modules.prediction.strategies.prediction.PrioritizeStrategy;
import modules.userContext.strategies.core.UserContextStrategy;
import modules.vision.services.GestureRecognitionService;
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
    private final SpotifyAuthenticator authenticator;
    private final GestureRecognitionService gestureService;

    public ContextController(ActiveSessionService sessionService, SpotifyAuthenticator authenticator, GestureRecognitionService gestureService) {
        this.sessionService = sessionService;
        this.authenticator = authenticator;
        this.gestureService = gestureService;
    }

    @PostMapping("/context")
    public ResponseEntity<Map<String, String>> handleContext(@RequestBody JsonNode payload) {
        System.out.println("Endpoint /api/context wurde aufgerufen!");
        try {
            UserContextDTO context = mapUserContext(payload);
            DjSessionController[] controllerRef = new DjSessionController[1];
            UserContextStrategy userContextStrategy = () -> {
                return controllerRef[0] != null ? controllerRef[0].getContext() : context;
            };

            DjSessionController controller = buildDjSessionWithMocks(userContextStrategy);
            controllerRef[0] = controller;

            sessionService.setActiveSession(controller);

            gestureService.resumeProcessing();
            new Thread(() -> startMusicSession(controller, context, userContextStrategy)).start();

            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid payload"));
        }
    }

    private void startMusicSession(DjSessionController controller, UserContextDTO context, UserContextStrategy userContextStrategy) {
        System.out.println("Starte Musik-Session mit empfangenem Context...");

        double elapsedMinutes = java.time.temporal.ChronoUnit.SECONDS.between(context.startTime(), java.time.LocalTime.now()) / 60.0;
        double safeElapsed = Math.max(0.0, elapsedMinutes);

        Map<Genre, Double> startWeights = context.timeline().getWeightsAt(safeElapsed);

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
        MusicPlayerAdapter player = new SpotifyAdapter(this.authenticator);
        MusicSourceAdapter sourceAdapter = new HybridSourceAdapter(localDb, new SpotifySourceAdapter());

        var prioritizeStrategy = new PrioritizeStrategy();

        List<PredictionStrategy> strategies = List.of(
                new MacroCurveStrategy(localDb, contextStrategy),
                new HistoryStrategy(historyRepo),
                prioritizeStrategy
        );
        var aggregator = new PredictionAggregator(strategies);

        return new DjSessionController(
                player, aggregator, sourceAdapter, historyRepo, playedSongRepo, contextStrategy.getUserContext(), prioritizeStrategy
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
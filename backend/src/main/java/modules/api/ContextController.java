package modules.api; // Passe das Package an

import com.fasterxml.jackson.databind.JsonNode;
import modules.core.DjSessionController;
import modules.music.repositories.PlayedSongRepository;
import modules.music.repositories.SessionHistoryRepository;
import modules.music.services.SessionBootstrapper;
import modules.music.strategies.music_player.spotify.SpotifyAdapter;
import modules.music.strategies.music_source.HybridSourceAdapter;
import modules.music.strategies.music_source.LocalSongDatabaseAdapter;
import modules.music.strategies.music_source.SpotifySourceAdapter;
import modules.music.structures.Track;
import modules.prediction.services.PredictionAggregator;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.strategies.prediction.HistoryStrategy;
import modules.prediction.strategies.prediction.MacroCurveStrategy;
import modules.userContext.strategies.core.UserContextStrategy;
import modules.vision.strategies.live_feedback.LiveFeedbackStrategyMock;
import modules.music.structures.Genre;
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
@CrossOrigin(origins = "*") // Ersetzt dein altes addCorsHeaders!
public class ContextController {

    private static volatile UserContextDTO latestUserContext;

    @PostMapping("/context")
    public ResponseEntity<Map<String, String>> handleContext(@RequestBody JsonNode payload) {
        System.out.println("Endpoint /api/context wurde aufgerufen!");

        try {
            // Spring hat den JSON-Body bereits in den 'payload' (JsonNode) umgewandelt
            UserContextDTO context = mapUserContext(payload);
            latestUserContext = context;

            // Session in einem neuen Thread starten (wie bisher)
            new Thread(() -> startMusicSession(context)).start();

            // Sende ein JSON { "status": "ok" } mit HTTP 200 zurück
            return ResponseEntity.ok(Map.of("status", "ok"));

        } catch (Exception e) {
            System.err.println("Fehler beim Verarbeiten des Payloads:");
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid payload"));
        }
    }

    public static UserContextDTO getLatestUserContext() {
        return latestUserContext;
    }

    // ==========================================================
    // DEINE BESTEHENDE GESCHÄFTSLOGIK AUS DER ALTEN APP.JAVA
    // ==========================================================

    private void startMusicSession(UserContextDTO context) {
        System.out.println("Starte Musik-Session mit empfangenem Context...");

        UserContextStrategy userContextStrategy = new UserContextStrategy() {
            @Override
            public UserContextDTO getUserContext() {
                return context;
            }
        };

        var playedSongRepo = new PlayedSongRepository();
        var localSongDatabaseAdapter = new LocalSongDatabaseAdapter(playedSongRepo, userContextStrategy);

        DjSessionController controller = getDjSessionController(localSongDatabaseAdapter, userContextStrategy, playedSongRepo);

        var sessionBootstrapper = new SessionBootstrapper(localSongDatabaseAdapter);
        Map<Genre, Double> startWeights = userContextStrategy.getUserContext().timeline().getWeightsAt(0.0);

        System.out.println("Start Genre: " + startWeights);
        Track entrySong = sessionBootstrapper.generateFirstTrack(startWeights);
        playedSongRepo.markAsPlayed(entrySong.id());

        System.out.println("Gefundener Entry Song: " + entrySong);

        controller.startSession(entrySong);
    }

    private DjSessionController getDjSessionController(
            LocalSongDatabaseAdapter localSongDatabaseAdapter,
            UserContextStrategy userContextStrategy,
            PlayedSongRepository playedSongRepo
    ) {

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
                playerMock, liveFeedbackMock, aggregator, hybridAdapter, history, playedSongRepo
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

        return new UserContextDTO(tempo, location, startTime, timeline, cooldown);
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
}
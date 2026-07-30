import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import modules.core.DjSessionController;
import modules.music.services.SessionBootstrapper;
import modules.music.strategies.music_player.spotify.SpotifyAdapter;
import modules.music.strategies.music_source.HybridSourceAdapter;
import modules.music.strategies.music_source.SpotifySourceAdapter;
import modules.music.structures.Genre;
import modules.userContext.structures.GenreTimeline;
import modules.userContext.structures.Location;
import modules.userContext.structures.TimelinePhase;
import modules.userContext.structures.UserContextDTO;

import modules.music.repositories.PlayedSongRepository;
import modules.music.repositories.SessionHistoryRepository;
import modules.music.strategies.music_source.LocalSongDatabaseAdapter;
import modules.music.structures.Track;
import modules.prediction.services.PredictionAggregator;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.strategies.prediction.HistoryStrategy;
import modules.prediction.strategies.prediction.MacroCurveStrategy;
import modules.userContext.strategies.core.UserContextStrategy;
import modules.vision.strategies.live_feedback.LiveFeedbackStrategyMock;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class App {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static volatile UserContextDTO latestUserContext;

    public static void main(String[] args) throws IOException {
        int port = Integer.getInteger("lumina.backend.port", 8081);
        startServer(port);
        System.out.println("LuminaDJ backend is running on http://localhost:" + port);
    }

    private static void startServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/context", App::handleContext);
        server.setExecutor(null);
        server.start();
    }

    private static void handleContext(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        System.out.println("Endpoint /api/context wurde aufgerufen!");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode payload = OBJECT_MAPPER.readTree(body);
            UserContextDTO context = mapUserContext(payload);
            latestUserContext = context;

            writeJson(exchange, 200, "{\"status\":\"ok\"}");

            new Thread(() -> startMusicSession(context)).start();

        } catch (Exception e) {
            System.err.println("Fehler beim Verarbeiten des Payloads:");
            e.printStackTrace();
            writeJson(exchange, 400, "{\"error\":\"Invalid payload\"}");
        }
    }

    private static void startMusicSession(UserContextDTO context) {
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

    private static DjSessionController getDjSessionController(
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

    private static UserContextDTO mapUserContext(JsonNode payload) {
        int tempo = payload.path("tempo").asInt(120);

        String locationRaw = payload.path("location").asText("Bar");
        Location location = Location.valueOf(locationRaw);

        String startTimeRaw = payload.path("startTime").asText(LocalTime.now().withNano(0).toString());
        LocalTime startTime = LocalTime.parse(startTimeRaw);

        GenreTimeline timeline = mapTimeline(payload.path("timeline"));
        int cooldown = payload.path("songCooldownMinutes").asInt(30);

        return new UserContextDTO(tempo, location, startTime, timeline, cooldown);
    }

    private static GenreTimeline mapTimeline(JsonNode timelineNode) {
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

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    public static UserContextDTO getLatestUserContext() {
        return latestUserContext;
    }
}
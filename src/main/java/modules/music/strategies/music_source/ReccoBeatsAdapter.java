package modules.music.strategies.music_source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import modules.music.strategies.core.MusicSourceAdapter;
import modules.music.structures.Track;
import modules.prediction.structures.PredictedAttributes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ReccoBeatsAdapter implements MusicSourceAdapter {

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReccoBeatsAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public Track getNextSong(PredictedAttributes target, Track currentSong) {
        System.out.println("ReccoBeats API: Suche besten Song für alle 6 Parameter...");

        try {
            String cleanTrackId = currentSong.id().replace("spotify:track:", "");

            // Formatierung sicherstellen (Punkte statt Kommas für die URL)
            String tEnergy = String.valueOf(target.energy()).replace(",", ".");
            String tTempo = String.valueOf(target.bpm()).replace(",", ".");
            String tDance = String.valueOf(target.danceability()).replace(",", ".");
            String tAcoustic = String.valueOf(target.acousticness()).replace(",", ".");
            String tInstrumental = String.valueOf(target.instrumentalness()).replace(",", ".");
            String tSpeech = String.valueOf(target.speechiness()).replace(",", ".");

            // Alle Parameter an die URL anhängen
            String url = String.format(
                    "https://api.reccobeats.com/v1/track/recommendation?seeds=%s&energy=%s&tempo=%s&danceability=%s&acousticness=%s&instrumentalness=%s&speechiness=%s&size=1",
                    cleanTrackId, tEnergy, tTempo, tDance, tAcoustic, tInstrumental, tSpeech
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                JsonNode tracksNode = root.has("content") ? root.path("content") : root.path("tracks");

                if (tracksNode.isArray() && tracksNode.size() > 0) {
                    JsonNode trackNode = tracksNode.get(0);

                    String newId = trackNode.path("id").asText();
                    String newName = trackNode.has("trackTitle") ? trackNode.path("trackTitle").asText() : trackNode.path("name").asText("Unknown Track");

                    String artistName = "Unknown Artist";
                    if (trackNode.has("name") && !trackNode.has("trackTitle")) {
                        artistName = trackNode.path("name").asText();
                    } else if (trackNode.has("artists") && trackNode.path("artists").isArray()) {
                        artistName = trackNode.path("artists").get(0).path("name").asText();
                    }

                    var newTrack = new Track(newId, newName, artistName,
                            target.energy(), target.bpm(),
                            target.danceability(), target.acousticness(),
                            target.instrumentalness(), target.speechiness());
                    System.out.println("ReccoBeats API: Song gefunden -> " + newTrack);

                    // Track mit allen 6 Werten zurückgeben
                    return newTrack;
                } else {
                    System.out.println("ReccoBeats API: Keine Tracks gefunden.");
                }
            } else {
                System.err.println("Fehler von der ReccoBeats API: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("API Call fehlgeschlagen: " + e.getMessage());
        }

        // Fallback-Song mit den Ziel-Attributen
        return new Track("7oVEtyuv9NBmnytsCIsY5I", "BURN IT DOWN", "Linkin Park",
                target.energy(), target.bpm(),
                target.danceability(), target.acousticness(),
                target.instrumentalness(), target.speechiness());
    }
}
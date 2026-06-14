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
import java.net.http.HttpTimeoutException;
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

            String tEnergy = String.valueOf(target.features().getOrDefault("energy", 0.5)).replace(",", ".");
            String tTempo = String.valueOf(target.features().getOrDefault("bpm", 120.0)).replace(",", ".");
            String tDance = String.valueOf(target.features().getOrDefault("danceability", 0.5)).replace(",", ".");
            String tAcoustic = String.valueOf(target.features().getOrDefault("acousticness", 0.5)).replace(",", ".");
            String tInstrumental = String.valueOf(target.features().getOrDefault("instrumentalness", 0.0)).replace(",", ".");
            String tSpeech = String.valueOf(target.features().getOrDefault("speechiness", 0.0)).replace(",", ".");

            String url = String.format(
                    "https://api.reccobeats.com/v1/track/recommendation?seeds=%s&energy=%s&tempo=%s&danceability=%s&acousticness=%s&instrumentalness=%s&speechiness=%s&size=1",
                    cleanTrackId, tEnergy, tTempo, tDance, tAcoustic, tInstrumental, tSpeech
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
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

                    if (trackNode.has("href") && !trackNode.path("href").isNull()) {
                        String href = trackNode.path("href").asText();
                        newId = href.substring(href.lastIndexOf("/") + 1);
                    }

                    if (newId.contains("-")) {
                        System.err.println("FEHLER: Konnte Spotify-ID nicht aus href extrahieren.");
                        System.err.println("JSON-Antwort zur Fehlersuche: \n" + trackNode.toPrettyString());
                        System.err.println("Nutze Fallback-Song, um Absturz zu verhindern...");

                        return new Track("7oVEtyuv9NBmnytsCIsY5I", "BURN IT DOWN", "Linkin Park", target.features());
                    }

                    String newName = trackNode.has("trackTitle") ? trackNode.path("trackTitle").asText() : trackNode.path("name").asText("Unknown Track");

                    String artistName = "Unknown Artist";
                    if (trackNode.has("name") && !trackNode.has("trackTitle")) {
                        artistName = trackNode.path("name").asText();
                    } else if (trackNode.has("artists") && trackNode.path("artists").isArray()) {
                        artistName = trackNode.path("artists").get(0).path("name").asText();
                    }

                    var newTrack = new Track(newId, newName, artistName, target.features());
                    System.out.println("ReccoBeats API: Song gefunden -> " + newTrack);

                    return newTrack;
                } else {
                    System.out.println("ReccoBeats API: Keine Tracks gefunden.");
                }
            } else {
                System.err.println("Fehler von der ReccoBeats API: HTTP " + response.statusCode());
            }
        } catch (HttpTimeoutException e) {
            System.err.println("API Call Timeout: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("API Call fehlgeschlagen: " + e.getMessage());
        }

        return new Track("7oVEtyuv9NBmnytsCIsY5I", "BURN IT DOWN", "Linkin Park", target.features());
    }
}
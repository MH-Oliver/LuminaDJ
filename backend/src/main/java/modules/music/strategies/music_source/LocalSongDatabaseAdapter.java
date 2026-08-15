package modules.music.strategies.music_source;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import modules.core.PathResolver;
import modules.music.repositories.PlayedSongRepository;
import modules.music.strategies.core.MusicSourceAdapter;
import modules.music.structures.Track;
import modules.prediction.structures.PredictedAttributes;
import modules.userContext.strategies.core.UserContextStrategy;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

@Service
public class LocalSongDatabaseAdapter implements MusicSourceAdapter {

    private final List<Track> database = new ArrayList<>();
    private final PlayedSongRepository playedRepo;
    private final UserContextStrategy contextStrategy;

    // GEFIXT: Drastisch erhöht, um unerwünschte Genres strikt zu blockieren
    private final double WRONG_GENRE_PENALITY = 0.15;

    private final Map<String, PredictedAttributes> genreCentroids = new HashMap<>();

    private record TrackDistance(Track track, double distance) {}

    public LocalSongDatabaseAdapter(PlayedSongRepository playedRepo, UserContextStrategy contextStrategy) {
        this.playedRepo = playedRepo;
        this.contextStrategy = contextStrategy;

        Config conf = ConfigFactory.load();
        String csvFilePath = PathResolver.resolve(conf.getString("songDatabase.path"));
        loadDatabase(csvFilePath);
    }

    private void loadDatabase(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            boolean isHeader = true;
            String csvSplitBy = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)";

            while ((line = br.readLine()) != null) {
                if (isHeader) { isHeader = false; continue; }

                String[] values = line.split(csvSplitBy, -1);
                if (values.length < 20) continue;

                try {
                    Track track = getTrack(values);
                    database.add(track);
                } catch (NumberFormatException e) {
                    System.err.println("Song Database | Zeile ist nicht im richtigen Format ");
                }
            }
            System.out.println("Song Database | " + database.size() + " Songs für das lokale Modell geladen");
            calculateCentroids();
        } catch (Exception e) {
            throw new IllegalArgumentException("Song Database | CSV kann nicht geladen werden, " +
                    "stelle sicher dass die Datei unter folgendem Pfad existiert");
        }
    }

    private void calculateCentroids() {
        Map<String, List<Track>> tracksByGenre = new HashMap<>();
        for (Track t : database) {
            tracksByGenre.computeIfAbsent(t.genre(), k -> new ArrayList<>()).add(t);
        }

        for (Map.Entry<String, List<Track>> entry : tracksByGenre.entrySet()) {
            String genre = entry.getKey();
            List<Track> tracks = entry.getValue();

            Map<String, Double> sums = new HashMap<>();
            for (Track t : tracks) {
                for (Map.Entry<String, Double> f : t.features().entrySet()) {
                    sums.merge(f.getKey(), f.getValue(), Double::sum);
                }
            }

            Map<String, Double> averages = new HashMap<>();
            for (Map.Entry<String, Double> f : sums.entrySet()) {
                averages.put(f.getKey(), f.getValue() / tracks.size());
            }

            genreCentroids.put(genre, new PredictedAttributes(averages));
        }
    }

    public PredictedAttributes getGenreCentroid(String genre) {
        return genreCentroids.getOrDefault(genre, new PredictedAttributes(Map.of(
                "energy", 0.5, "bpm", 120.0 / 200.0, "danceability", 0.5,
                "acousticness", 0.5, "instrumentalness", 0.0, "speechiness", 0.05, "valence", 0.5
        )));
    }

    private static Track getTrack(String[] values) {
        String id = values[1].replace("\"", "").trim();
        String artists = values[2].replace("\"", "").trim();
        String name = values[4].replace("\"", "").trim();
        String genre = values[20].replace("\"", "").trim();

        Map<String, Double> features = new HashMap<>();
        features.put("danceability", Double.parseDouble(values[8]));
        features.put("energy", Double.parseDouble(values[9]));
        features.put("speechiness", Double.parseDouble(values[13]));
        features.put("acousticness", Double.parseDouble(values[14]));
        features.put("instrumentalness", Double.parseDouble(values[15]));
        features.put("valence", Double.parseDouble(values[17]));
        features.put("bpm", Double.parseDouble(values[18]) / 200.0);

        return new Track(id, name, artists, genre, features);
    }

    @Override
    public Track getNextSong(PredictedAttributes target, Track currentSong) {
        return getTopK(target, currentSong, 1).getFirst();
    }

    public List<Track> getTopK(PredictedAttributes target, Track currentSong, int k) {
        PriorityQueue<TrackDistance> maxHeap = new PriorityQueue<>(
                k, Comparator.comparingDouble(TrackDistance::distance).reversed()
        );

        int cooldown = contextStrategy.getUserContext().songCooldownMinutes();

        for (Track candidate : database) {
            if (candidate.id().equals(currentSong.id())) continue;

            if (!playedRepo.isPlayable(candidate.id(), cooldown)) {
                continue;
            }

            double distance = calculateNormalizedDistance(target, candidate);
            double penalty = 0.0;

            if (target.genreWeights() != null && !target.genreWeights().isEmpty()) {
                double targetWeight = target.genreWeights().getOrDefault(candidate.genre(), 0.0);
                penalty = WRONG_GENRE_PENALITY * (1.0 - targetWeight);
            } else {
                if (currentSong.genre() != null && !currentSong.genre().equalsIgnoreCase(candidate.genre())) {
                    penalty = WRONG_GENRE_PENALITY;
                }
            }

            distance += penalty;
            maxHeap.offer(new TrackDistance(candidate, distance));

            if (maxHeap.size() > k) {
                maxHeap.poll();
            }
        }

        List<Track> topK = new ArrayList<>();
        while (!maxHeap.isEmpty()) {
            topK.addFirst(maxHeap.poll().track());
        }
        return topK;
    }

    private double calculateNormalizedDistance(PredictedAttributes target, Track track) {
        double sum = 0;
        for (String key : target.features().keySet()) {
            double targetValue = target.features().get(key);
            double trackValue = track.features().getOrDefault(key, 0.0);
            sum += Math.pow(targetValue - trackValue, 2);
        }
        return Math.sqrt(sum);
    }
}
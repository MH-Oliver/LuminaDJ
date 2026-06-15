package modules.music.strategies.music_source;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import modules.music.strategies.core.MusicSourceAdapter;
import modules.music.structures.Track;
import modules.prediction.structures.PredictedAttributes;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

// "Spotify Tracks Dataset" (von Maharshi Pandya) --> Kaggle
public class LocalSongDatabaseAdapter implements MusicSourceAdapter {

    private final List<Track> database = new ArrayList<>();

    private final double WRONG_GENRE_PENALITY = 0.15;

    private record TrackDistance(Track track, double distance) {}

    public LocalSongDatabaseAdapter() {
        Config conf = ConfigFactory.load();
        String csvFilePath = conf.getString("songDatabase.path");

        loadDatabase(csvFilePath);
    }

    /**
     * Lädt den 114k Spotify-Datensatz beim Programmstart in den Arbeitsspeicher.
     */
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

        } catch (Exception e) {
            throw new IllegalArgumentException("Song Database | CSV kann nicht geladen werden, " +
                    "stelle sicher dass die Datei unter folgendem Pfad existiert");
        }
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

    /**
     * Findet die k besten (nächsten) Songs mittels einer PriorityQueue.
     */
    public List<Track> getTopK(PredictedAttributes target, Track currentSong, int k) {
        PriorityQueue<TrackDistance> maxHeap = new PriorityQueue<>(
                k, Comparator.comparingDouble(TrackDistance::distance).reversed()
        );

        for (Track candidate : database) {
            if (candidate.id().equals(currentSong.id())) continue;

            double distance = calculateNormalizedDistance(target, candidate);

            if (currentSong.genre() != null && !currentSong.genre().equalsIgnoreCase(candidate.genre())) {
                distance += WRONG_GENRE_PENALITY;
            }

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

    /**
     * Berechnet die Euklidische Distanz zwischen dem Ziel-Vektor und einem Song in der Datenbank.
     */
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
package modules.prediction.strategies.prediction;

import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictionFactor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrioritizeStrategy implements PredictionStrategy {
    private final List<Track> prioritizedTracks = new ArrayList<>();

    public void addTrack(Track track) {
        prioritizedTracks.add(track);
        System.out.println("PrioritizeStrategy: Track priorisiert -> " + track.name() + " (" + track.genre() + ")");
    }

    @Override
    public double getWeight() {
        return 3.0;
    }

    @Override
    public PredictionFactor calculate(Track currentTrack) {
        if (prioritizedTracks.isEmpty()) {
            return new PredictionFactor(new HashMap<>(), new HashMap<>());
        }

        Map<String, Double> avgFeatures = new HashMap<>();
        for (Track t : prioritizedTracks) {
            for (Map.Entry<String, Double> f : t.features().entrySet()) {
                avgFeatures.merge(f.getKey(), f.getValue(), Double::sum);
            }
        }

        int count = prioritizedTracks.size();
        for (String key : avgFeatures.keySet()) {
            avgFeatures.put(key, avgFeatures.get(key) / count);
        }

        var result = new PredictionFactor(avgFeatures, new HashMap<>());
        System.out.println("PrioritizeStrategy: " + result);
        return result;
    }
}
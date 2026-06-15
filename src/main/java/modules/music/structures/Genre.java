package modules.music.structures;

import modules.prediction.structures.PredictedAttributes;

import java.util.Map;

public enum Genre {
    HIP_HOP("HIP-HOP", new PredictedAttributes(Map.of("energy", 0.75, "bpm", 90.0 / 200.0, "danceability", 0.85, "acousticness", 0.15, "instrumentalness", 0.00, "speechiness", 0.35)), "6AI3ezQ4o3HUoP6Dhudph3"),
    POP("POP", new PredictedAttributes(Map.of("energy", 0.70, "bpm", 115.0 / 200.0, "danceability", 0.75, "acousticness", 0.20, "instrumentalness", 0.00, "speechiness", 0.10)), "7qiZfU4dY1lWllzX7mPBI3"),
    ROCK("ROCK", new PredictedAttributes(Map.of("energy", 0.85, "bpm", 115.0 / 200.0, "danceability", 0.40, "acousticness", 0.05, "instrumentalness", 0.10, "speechiness", 0.05)), "2zYzyRzz6pRmhPzyfMEC8s"),
    COUNTRY("COUNTRY", new PredictedAttributes(Map.of("energy", 0.60, "bpm", 110.0 / 200.0, "danceability", 0.55, "acousticness", 0.40, "instrumentalness", 0.00, "speechiness", 0.05)), "1QbOvACeYanja5pbnJbAmk"),
    EDM("dance", new PredictedAttributes(Map.of("energy", 0.92, "bpm", 132.0 / 200.0, "danceability", 0.80, "acousticness", 0.01, "instrumentalness", 0.85, "speechiness", 0.04)), "6gdDu39yYqPcaTgCwYEW8i"),
    LATIN("LATIN", new PredictedAttributes(Map.of("energy", 0.80, "bpm", 105.0 / 200.0, "danceability", 0.85, "acousticness", 0.20, "instrumentalness", 0.00, "speechiness", 0.10)), "6habFhsOp2NvshLv26DqMb"),
    K_POP("K-POP", new PredictedAttributes(Map.of("energy", 0.85, "bpm", 120.0 / 200.0, "danceability", 0.80, "acousticness", 0.10, "instrumentalness", 0.00, "speechiness", 0.10)), "1CPZ5BxNNd0n0nF4Orb9JS"),
    RNB_SOUL("R&B/SOUL", new PredictedAttributes(Map.of("energy", 0.50, "bpm", 85.0 / 200.0, "danceability", 0.65, "acousticness", 0.30, "instrumentalness", 0.00, "speechiness", 0.10)), "0I3q5fE6wg7LIfHGngUTnV"),
    JAZZ("JAZZ", new PredictedAttributes(Map.of("energy", 0.35, "bpm", 100.0 / 200.0, "danceability", 0.45, "acousticness", 0.75, "instrumentalness", 0.40, "speechiness", 0.05)), "43iIQbw5hx986dUEZbr3eN"),
    CLASSICAL("CLASSICAL", new PredictedAttributes(Map.of("energy", 0.15, "bpm", 80.0 / 200.0, "danceability", 0.20, "acousticness", 0.95, "instrumentalness", 0.90, "speechiness", 0.02)), "17mTPR6CmBQu8AsgBRPsw4");

    private final String displayName;
    private final PredictedAttributes attributes;
    private final String seedTrackId;

    Genre(String displayName, PredictedAttributes attributes, String seedTrackId) {
        this.displayName = displayName;
        this.attributes = attributes;
        this.seedTrackId = seedTrackId;
    }

    public String getDisplayName() { return displayName; }
    public PredictedAttributes getAttributes() { return attributes; }
    public String getSeedTrackId() { return seedTrackId; }
}
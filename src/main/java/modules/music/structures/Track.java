package modules.music.structures;

public record Track(
        String id,
        String name,
        String author,
        double energy,
        double bpm,
        double danceability,
        double acousticness,
        double instrumentalness,
        double speechiness
) {}
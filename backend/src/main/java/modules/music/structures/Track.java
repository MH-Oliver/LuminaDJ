package modules.music.structures;

import java.util.Map;

public record Track(
        String id,
        String name,
        String author,
        String genre,
        Map<String, Double> features
) {}
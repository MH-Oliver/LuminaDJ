package modules.music.structures;

import java.util.Map;

public record Track(
        String id,
        String name,
        String author,
        Map<String, Double> features
) {}
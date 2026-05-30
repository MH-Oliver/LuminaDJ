package modules.prediction.structures;

/**
 * Repräsentiert die berechneten Ziel-Attribute für den nächsten Song
 * @param energy
 * @param bpm
 */
public record PredictedAttributes(
        double energy,
        double bpm,
        double danceability,
        double acousticness,
        double instrumentalness,
        double speechiness
) {}

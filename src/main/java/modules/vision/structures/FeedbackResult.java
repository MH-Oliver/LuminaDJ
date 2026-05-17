package modules.vision.structures;

/**
 * Repräsentiert das Ergebnis der Kamera-Auswertung
 * @param isPositiveTrend
 * @param intensity
 */
public record FeedbackResult(boolean isPositiveTrend, double intensity) {}

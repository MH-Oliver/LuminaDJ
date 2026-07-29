package modules.vision.structures;

/**
 * Repräsentiert das Ergebnis der Kamera-Auswertung
 * @param isPositiveTrend
 * @param intensity Wert zwischen 0 und 1
 */
public record FeedbackResult(boolean isPositiveTrend, double intensity) {}

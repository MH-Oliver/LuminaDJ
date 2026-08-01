package modules.vision.structures;

import org.opencv.core.Point;

/**
 * Repräsentiert die 21 MediaPipe-Hand-Keypoints eines erkannten Handskeletts.
 * Koordinaten (points) sind Pixel-Koordinaten im Original-Frame (nicht normalisiert).
 * z enthält die relative Tiefe pro Keypoint (kleiner = näher an der Kamera), grob skaliert.
 *
 * @param points     21 Keypoints in Original-Frame-Pixelkoordinaten (Reihenfolge gemäß MediaPipe-Hand-Schema)
 * @param z          21 relative Tiefenwerte, gleiche Reihenfolge wie points
 * @param confidence Konfidenz, dass es sich überhaupt um eine Hand handelt (0..1)
 */
public record HandLandmarks(Point[] points, float[] z, float confidence) {
}
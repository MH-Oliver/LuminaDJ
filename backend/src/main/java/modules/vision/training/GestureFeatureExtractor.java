package modules.vision.training;

import modules.vision.structures.HandLandmarks;
import org.opencv.core.Point;

/**
 * Wandelt rohe {@link HandLandmarks} in einen normalisierten Feature-Vektor um, der für die
 * Gesten-Klassifikation genutzt wird.
 * <p>
 * Normalisierung:
 * - Zentrierung auf das Handgelenk (Landmark 0), damit die Position der Hand im Bild
 *   (Kamera-Ausschnitt) keine Rolle spielt.
 * - Skalierung auf die Distanz Handgelenk -> Mittelfinger-Basis (Landmark 9), damit die
 *   Handgröße bzw. der Abstand zur Kamera keine Rolle spielt.
 * <p>
 * Ergebnis: ein 42-dimensionaler Vektor (x,y je der 21 Landmarks), unabhängig von Position
 * und Größe der Hand im Bild.
 */
public class GestureFeatureExtractor {

    public static double[] toFeatureVector(HandLandmarks landmarks) {
        Point[] pts = landmarks.points();
        Point wrist = pts[0];
        Point middleFingerBase = pts[9];

        double refDistance = distance(wrist, middleFingerBase);
        if (refDistance < 1e-6) {
            refDistance = 1.0;
        }

        double[] features = new double[pts.length * 2];
        for (int i = 0; i < pts.length; i++) {
            features[i * 2] = (pts[i].x - wrist.x) / refDistance;
            features[i * 2 + 1] = (pts[i].y - wrist.y) / refDistance;
        }
        return features;
    }

    private static double distance(Point a, Point b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
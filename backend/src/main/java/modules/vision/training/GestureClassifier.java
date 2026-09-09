package modules.vision.training;

import java.io.*;
import java.util.*;

/**
 * Einfacher k-Nearest-Neighbor-Klassifikator für Gesten-Feature-Vektoren (siehe
 * {@link GestureFeatureExtractor}). Bewusst ohne externe ML-Bibliothek umgesetzt:
 * - "Training" ist bei k-NN nur das Abspeichern der Beispiele - keine Trainingszeit,
 *   kein Risiko einer falschen/veralteten Bibliotheks-API.
 * - Bei wenigen, gut trennbaren Klassen (hier: 5 Handgesten) liefert k-NN vergleichbare
 *   Ergebnisse wie aufwändigere Modelle, ist aber trivial zu verstehen und zu debuggen.
 * - Speicherformat ist eine einfache, menschenlesbare CSV-Datei (Label + 42 Werte pro Zeile).
 */
public class GestureClassifier {

    private final List<double[]> trainingFeatures = new ArrayList<>();
    private final List<String> trainingLabels = new ArrayList<>();
    private final int k;

    public GestureClassifier(int k) {
        this.k = k;
    }

    public void addExample(String label, double[] featureVector) {
        trainingLabels.add(label);
        trainingFeatures.add(featureVector);
    }

    public int size() {
        return trainingFeatures.size();
    }

    public List<double[]> getFeatures() {
        return Collections.unmodifiableList(trainingFeatures);
    }

    public List<String> getLabels() {
        return Collections.unmodifiableList(trainingLabels);
    }

    /**
     * Klassifiziert einen Feature-Vektor. Gibt das Label der Mehrheit unter den k nächsten
     * Nachbarn zurück, zusammen mit der "Konfidenz" (Anteil der k Nachbarn, die für dieses
     * Label gestimmt haben).
     */
    public Prediction classify(double[] queryFeatures) {
        if (trainingFeatures.isEmpty()) {
            throw new IllegalStateException("Klassifikator hat keine Trainingsbeispiele.");
        }

        int effectiveK = Math.min(k, trainingFeatures.size());
        List<Neighbor> neighbors = new ArrayList<>(trainingFeatures.size());
        for (int i = 0; i < trainingFeatures.size(); i++) {
            double dist = euclideanDistance(queryFeatures, trainingFeatures.get(i));
            neighbors.add(new Neighbor(dist, trainingLabels.get(i)));
        }
        neighbors.sort(Comparator.comparingDouble(n -> n.distance));

        Map<String, Integer> votes = new HashMap<>();
        for (int i = 0; i < effectiveK; i++) {
            votes.merge(neighbors.get(i).label, 1, Integer::sum);
        }

        String bestLabel = null;
        int bestVotes = -1;
        for (Map.Entry<String, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > bestVotes) {
                bestVotes = entry.getValue();
                bestLabel = entry.getKey();
            }
        }

        double confidence = bestVotes / (double) effectiveK;
        return new Prediction(bestLabel, confidence);
    }

    private static double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /**
     * Speichert alle Trainingsbeispiele als einfache CSV-Datei (eine Zeile pro Beispiel:
     * label,feat0,feat1,...,feat41). Menschenlesbar, leicht zu inspizieren, keine
     * Java-Serialisierungs-Versionsprobleme.
     */
    public void save(File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            for (int i = 0; i < trainingFeatures.size(); i++) {
                StringBuilder line = new StringBuilder(trainingLabels.get(i));
                for (double value : trainingFeatures.get(i)) {
                    line.append(',').append(value);
                }
                writer.println(line);
            }
        }
    }

    /**
     * Hängt EIN Beispiel an eine CSV-Datei an (erzeugt die Datei falls nötig). Damit kann
     * z.B. DataCollectorApp Landmarks direkt beim Sammeln speichern, ohne die wachsende
     * Datei jedes Mal komplett neu schreiben zu müssen.
     */
    public static void appendExample(File file, String label, double[] featureVector) throws IOException {
        StringBuilder line = new StringBuilder(label);
        for (double value : featureVector) {
            line.append(',').append(value);
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            writer.println(line);
        }
    }

    public static GestureClassifier load(File file, int k) throws IOException {
        GestureClassifier classifier = new GestureClassifier(k);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                String label = parts[0];
                double[] features = new double[parts.length - 1];
                for (int i = 1; i < parts.length; i++) {
                    features[i - 1] = Double.parseDouble(parts[i]);
                }
                classifier.addExample(label, features);
            }
        }
        return classifier;
    }

    public record Prediction(String label, double confidence) {}

    private record Neighbor(double distance, String label) {}
}
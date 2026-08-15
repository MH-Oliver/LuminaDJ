package modules.vision.training;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Liest die von {@link DataCollectorApp} direkt beim Sammeln gespeicherten Landmark-Features
 * (training_data/gesture_landmarks.csv) ein, macht einen Train/Test-Split pro Geste, wertet
 * die Genauigkeit des k-NN-Klassifikators aus und speichert den finalen, auf allen Daten
 * trainierten Klassifikator.
 * <p>
 * Bewusst KEIN erneutes Einlesen/Erkennen von Bildern hier: Die Landmarks wurden schon beim
 * Sammeln live berechnet (mit der strengeren Live-Konfidenzschwelle) - das hier ist nur noch
 * ein einfacher Trainings-/Auswertungsschritt auf bereits vorhandenen Zahlen.
 */
public class TrainGestureClassifierApp {

    private static final int K = 5;
    private static final double TEST_SPLIT = 0.2; // 20% pro Geste für die Auswertung zurückhalten

    public static void main(String[] args) throws IOException {
        File landmarksCsvFile = new File("backend/src/main/resources/training_data/gesture_landmarks.csv");
        if (!landmarksCsvFile.exists()) {
            landmarksCsvFile = new File("src/main/resources/training_data/gesture_landmarks.csv");
        }
        if (!landmarksCsvFile.exists()) {
            System.err.println("[FEHLER] Keine Landmark-Daten gefunden unter: " + landmarksCsvFile.getAbsolutePath()
                    + "\nErst mit DataCollectorApp Trainingsdaten sammeln.");
            return;
        }

        GestureClassifier allData = GestureClassifier.load(landmarksCsvFile, K);
        System.out.println("[INFO] " + allData.size() + " Landmark-Beispiele insgesamt geladen.");

        // Nach Geste gruppieren, um pro Klasse einen Train/Test-Split zu machen (damit auch
        // kleine Klassen im Test-Set vertreten sind).
        Map<String, List<double[]>> featuresByLabel = new LinkedHashMap<>();
        List<double[]> features = allData.getFeatures();
        List<String> labels = allData.getLabels();
        for (int i = 0; i < allData.size(); i++) {
            featuresByLabel.computeIfAbsent(labels.get(i), l -> new ArrayList<>()).add(features.get(i));
        }

        System.out.println("==================================================");
        for (Map.Entry<String, List<double[]>> entry : featuresByLabel.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue().size() + " Beispiele");
        }
        System.out.println("==================================================");

        GestureClassifier trainSet = new GestureClassifier(K);
        List<double[]> testFeatures = new ArrayList<>();
        List<String> testLabels = new ArrayList<>();

        Random random = new Random(42); // fester Seed, damit der Split reproduzierbar ist
        for (Map.Entry<String, List<double[]>> entry : featuresByLabel.entrySet()) {
            String label = entry.getKey();
            List<double[]> examples = new ArrayList<>(entry.getValue());
            Collections.shuffle(examples, random);

            int testCount = Math.max(1, (int) (examples.size() * TEST_SPLIT));
            for (int i = 0; i < examples.size(); i++) {
                if (i < testCount) {
                    testFeatures.add(examples.get(i));
                    testLabels.add(label);
                } else {
                    trainSet.addExample(label, examples.get(i));
                }
            }
        }

        // Auswertung auf dem zurückgehaltenen Test-Set
        int correct = 0;
        Map<String, Map<String, Integer>> confusionMatrix = new TreeMap<>();
        for (int i = 0; i < testFeatures.size(); i++) {
            GestureClassifier.Prediction prediction = trainSet.classify(testFeatures.get(i));
            String actual = testLabels.get(i);
            if (prediction.label().equals(actual)) correct++;

            confusionMatrix.computeIfAbsent(actual, l -> new TreeMap<>())
                    .merge(prediction.label(), 1, Integer::sum);
        }

        double accuracy = testFeatures.isEmpty() ? 0 : correct / (double) testFeatures.size();
        System.out.printf("[ERGEBNIS] Genauigkeit auf Test-Set: %.1f%% (%d von %d)%n",
                accuracy * 100, correct, testFeatures.size());
        System.out.println("[ERGEBNIS] Konfusionsmatrix (Zeile = tatsächliche Geste, Spalte = vorhergesagte Häufigkeiten):");
        for (Map.Entry<String, Map<String, Integer>> row : confusionMatrix.entrySet()) {
            System.out.println("  " + row.getKey() + ": " + row.getValue());
        }

        // Finalen Klassifikator einfach als Kopie der kompletten Daten speichern (der
        // Test-Split diente nur der Bewertung oben) - das ist die Datei, die die App später
        // zur Laufzeit lädt.
        File modelFile = new File(landmarksCsvFile.getParentFile().getParentFile(), "models/gesture_classifier.csv");
        modelFile.getParentFile().mkdirs();
        allData.save(modelFile);

        System.out.println("==================================================");
        System.out.println("[GESPEICHERT] Klassifikator (" + allData.size() + " Beispiele) unter: "
                + modelFile.getAbsolutePath());
    }
}
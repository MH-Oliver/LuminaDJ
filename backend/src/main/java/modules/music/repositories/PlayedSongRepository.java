package modules.music.repositories;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import modules.core.PathResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Service
public class PlayedSongRepository {
    private final File historyFile;

    private final Map<String, LocalDateTime> playedHistory;

    public PlayedSongRepository() {
        Config conf = ConfigFactory.load();
        String filePath = PathResolver.resolve(conf.getString("playedSongs.path"));

        this.historyFile = new File(filePath);
        this.playedHistory = new ConcurrentHashMap<>();
        loadHistory();
    }

    /**
     * Markiert einen Song als gespielt und speichert die aktualisierte Liste in der CSV.
     */
    public void markAsPlayed(String trackId) {
        playedHistory.put(trackId, LocalDateTime.now());
        saveHistory();
    }

    /**
     * Prüft, ob ein Song basierend auf dem Cooldown (in Minuten) gespielt werden darf.
     */
    public boolean isPlayable(String trackId, int cooldownMinutes) {
        if (!playedHistory.containsKey(trackId)) {
            return true;
        }

        LocalDateTime lastPlayed = playedHistory.get(trackId);
        long minutesSincePlayed = ChronoUnit.MINUTES.between(lastPlayed, LocalDateTime.now());

        return minutesSincePlayed >= cooldownMinutes;
    }

    /**
     * Leert die Historie (z.B. für einen neuen Abend) und überschreibt die CSV.
     */
    public void resetHistory() {
        playedHistory.clear();
        saveHistory();
    }

    /**
     * Lädt die Historie aus der CSV-Datei beim Programmstart.
     */
    private void loadHistory() {
        if (!historyFile.exists()) {
            return; // Nichts zu laden, starte mit leerer Map
        }

        try (BufferedReader br = new BufferedReader(new FileReader(historyFile))) {
            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                // Überspringe den CSV-Header
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length == 2) {
                    String trackId = parts[0].trim();
                    try {
                        LocalDateTime timestamp = LocalDateTime.parse(parts[1].trim());
                        playedHistory.put(trackId, timestamp);
                    } catch (DateTimeParseException e) {
                        System.err.println("Ungültiges Datumsformat in Historie übersprungen: " + parts[1]);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Konnte Song-Historie nicht lesen. Starte mit leerer Historie.");
        }
    }

    /**
     * Schreibt den aktuellen Stand synchronisiert in die CSV-Datei.
     */
    private synchronized void saveHistory() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(historyFile))) {
            // Schreibe den CSV-Header
            bw.write("track_id,last_played_at\n");

            // Schreibe alle Einträge
            for (Map.Entry<String, LocalDateTime> entry : playedHistory.entrySet()) {
                bw.write(entry.getKey() + "," + entry.getValue().toString() + "\n");
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Speichern der Song-Historie in CSV: " + e.getMessage());
        }
    }
}
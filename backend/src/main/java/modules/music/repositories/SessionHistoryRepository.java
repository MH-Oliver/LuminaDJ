package modules.music.repositories;

import modules.music.structures.HistoryEntry;
import modules.music.structures.Track;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
@Service
public class SessionHistoryRepository {
    private final List<HistoryEntry> history = new ArrayList<>();

    /**
     * Fügt einen gespielten Song samt dem dazugehörigen Crowd-Feedback zur Historie hinzu.
     */
    // Signatur und Instanziierung anpassen
    public void addEntry(Track track) {
        history.add(new HistoryEntry(track));
    }

    /**
     * Gibt eine unveränderbare (read-only) Kopie der bisherigen Historie zurück.
     */
    public List<HistoryEntry> getHistory() {
        return new ArrayList<>(history);
    }
}
package modules.music.repositories;

import modules.music.structures.HistoryEntry;
import modules.music.structures.Track;
import modules.vision.structures.FeedbackResult;

import java.util.ArrayList;
import java.util.List;

public class SessionHistoryRepository {
    private final List<HistoryEntry> history = new ArrayList<>();

    /**
     * Fügt einen gespielten Song samt dem dazugehörigen Crowd-Feedback zur Historie hinzu.
     */
    public void addEntry(Track track, FeedbackResult feedback) {
        history.add(new HistoryEntry(track, feedback));
    }

    /**
     * Gibt eine unveränderbare (read-only) Kopie der bisherigen Historie zurück.
     */
    public List<HistoryEntry> getHistory() {
        return new ArrayList<>(history);
    }
}
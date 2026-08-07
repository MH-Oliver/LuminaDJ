package modules.userContext.structures;

import modules.music.structures.Genre;
import java.util.List;
import java.util.Map;

public class GenreTimeline {
    private final List<TimelinePhase> phases;

    public GenreTimeline(List<TimelinePhase> phases) {
        this.phases = phases;
    }

    // NEU: Diese Methode ist ZWINGEND ERFORDERLICH, damit Spring Boot
    // das Array "phases" als JSON an das Frontend senden kann!
    public List<TimelinePhase> getPhases() {
        return phases;
    }

    /**
     * Berechnet die prozentuale Mischung der Genres zum aktuellen Zeitpunkt.
     */
    public Map<Genre, Double> getWeightsAt(double elapsedMinutes) {
        double currentStartTime = 0.0;
        for (int i = 0; i < phases.size(); i++) {
            TimelinePhase phase = phases.get(i);
            double phaseEndTime = currentStartTime + phase.durationMinutes();
            double transitionStartTime = phaseEndTime - phase.transitionOutMinutes();

            if (elapsedMinutes < phaseEndTime) {
                if (elapsedMinutes < transitionStartTime || i == phases.size() - 1) {
                    // Vollständig im aktuellen Genre
                    return Map.of(phase.genre(), 1.0);
                } else {
                    // Linearer Übergang (Crossfade) zum nächsten Genre
                    double progress = (elapsedMinutes - transitionStartTime) / phase.transitionOutMinutes();
                    TimelinePhase nextPhase = phases.get(i + 1);
                    return Map.of(
                            phase.genre(), 1.0 - progress,
                            nextPhase.genre(), progress
                    );
                }
            }
            currentStartTime = phaseEndTime;
        }
        // Falls die Zeit die geplante Timeline überschreitet, bleibe beim letzten Genre
        return Map.of(phases.getLast().genre(), 1.0);
    }
}
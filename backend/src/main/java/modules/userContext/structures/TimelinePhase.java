package modules.userContext.structures;

import modules.music.structures.Genre;

/**
 * Repräsentiert einen Block auf der Timeline.
 * @param durationMinutes Wie lange dieses Genre die Führung hat.
 * @param transitionOutMinutes Wie viele Minuten VOR Ende der Phase der Übergang zum nächsten Genre beginnt.
 */
public record TimelinePhase(
        Genre genre,
        double durationMinutes,
        double transitionOutMinutes) {}
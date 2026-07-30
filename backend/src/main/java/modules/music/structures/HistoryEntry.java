package modules.music.structures;

import modules.vision.structures.FeedbackResult;

/**
 * Datenstruktur für einen einzelnen Eintrag im Verlauf
 */
public record HistoryEntry(Track track, FeedbackResult feedback) {}
package modules.userContext.factories;

import modules.music.structures.Genre;
import modules.music.structures.Track;
import modules.userContext.structures.GenreTimeline;
import modules.userContext.structures.SessionVibe;
import modules.userContext.structures.TimelinePhase;

import java.util.List;

public class TimelineFactory {

    public static GenreTimeline createTimelineForVibe(SessionVibe vibe) {
        return switch (vibe) {
            case CHILLOUT_LOUNGE -> new GenreTimeline(List.of(
                    new TimelinePhase(Genre.ACOUSTIC, 60.0, 15.0),
                    new TimelinePhase(Genre.AMBIENT, 120.0, 0.0)
            ));

            case CLUB_ESCALATION -> new GenreTimeline(List.of(
                    new TimelinePhase(Genre.DEEP_HOUSE, 30.0, 10.0),
                    new TimelinePhase(Genre.EDM, 60.0, 10.0),
                    new TimelinePhase(Genre.TECHNO, 120.0, 0.0)
            ));

            case WEDDING_PARTY -> new GenreTimeline(List.of(
                    new TimelinePhase(Genre.POP, 60.0, 20.0),
                    new TimelinePhase(Genre.DISCO, 60.0, 15.0),
                    new TimelinePhase(Genre.ROCK, 60.0, 0.0)
            ));

            case FOCUS_WORK -> new GenreTimeline(List.of(
                    new TimelinePhase(Genre.CLASSICAL, 240.0, 0.0)
            ));
        };
    }

    // Generiert eine unendlich lange Phase basierend auf dem Genre des gewählten Songs
    public static GenreTimeline createTimelineFromSeedSong(Track seedSong) {
        Genre seedGenre = Genre.fromString(seedSong.genre());

        return new GenreTimeline(List.of(
                new TimelinePhase(seedGenre, 1000.0, 0.0)
        ));
    }
}
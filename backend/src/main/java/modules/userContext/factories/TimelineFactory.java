package modules.userContext.factories;

import modules.music.structures.Genre;
import modules.music.structures.Track;
import modules.userContext.structures.GenreTimeline;
import modules.userContext.structures.SessionVibe;
import modules.userContext.structures.TimelinePhase;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class TimelineFactory {

    public static GenreTimeline createTimelineForVibe(SessionVibe vibe) {
        return switch (vibe) {

            case SUNDAY_MORNING_COFFEE -> new GenreTimeline(List.of(
                    new TimelinePhase(Genre.ACOUSTIC, 20.0, 5.0),
                    new TimelinePhase(Genre.SINGER_SONGWRITER, 20.0, 5.0),
                    new TimelinePhase(Genre.FOLK, 20.0, 5.0),
                    new TimelinePhase(Genre.BLUES, 60.0, 0.0)
            ));

            case SUMMER_BBQ -> new GenreTimeline(List.of(
                    new TimelinePhase(Genre.RNB_SOUL, 15.0, 5.0),
                    new TimelinePhase(Genre.REGGAE, 15.0, 5.0),
                    new TimelinePhase(Genre.LATINO, 15.0, 5.0),
                    new TimelinePhase(Genre.AFROBEAT, 15.0, 5.0),
                    new TimelinePhase(Genre.DISCO, 60.0, 0.0)
            ));

            case PRE_GAME_HYPE -> new GenreTimeline(List.of(
                    new TimelinePhase(Genre.HIP_HOP, 15.0, 5.0),
                    new TimelinePhase(Genre.DANCEHALL, 15.0, 5.0),
                    new TimelinePhase(Genre.HOUSE, 15.0, 5.0),
                    new TimelinePhase(Genre.DEEP_HOUSE, 15.0, 5.0),
                    new TimelinePhase(Genre.TECHNO, 60.0, 0.0)
            ));

            case LATE_NIGHT_DRIVE -> new GenreTimeline(List.of(
                    new TimelinePhase(Genre.CHILL, 15.0, 5.0),
                    new TimelinePhase(Genre.SYNTH_POP, 15.0, 5.0),
                    new TimelinePhase(Genre.TRANCE, 15.0, 5.0),
                    new TimelinePhase(Genre.TRIP_HOP, 60.0, 0.0)
            ));

            case WORKOUT -> new GenreTimeline(List.of(
                    new TimelinePhase(Genre.DEEP_HOUSE, 15.0, 5.0),
                    new TimelinePhase(Genre.TECHNO, 15.0, 5.0),
                    new TimelinePhase(Genre.EDM, 15.0, 5.0),
                    new TimelinePhase(Genre.HARDSTYLE, 60.0, 0.0)
            ));

            case DEEP_FOCUS -> new GenreTimeline(List.of(
                    new TimelinePhase(Genre.PIANO, 20.0, 5.0),
                    new TimelinePhase(Genre.AMBIENT, 20.0, 5.0),
                    new TimelinePhase(Genre.IDM, 20.0, 5.0),
                    new TimelinePhase(Genre.CLASSICAL, 60.0, 0.0)
            ));
        };
    }
    public static GenreTimeline createTimelineFromSeedSong(Track seedSong) {
        Genre seedGenre = Genre.fromString(seedSong.genre());

        return new GenreTimeline(List.of(
                new TimelinePhase(seedGenre, 1000.0, 0.0)
        ));
    }
}
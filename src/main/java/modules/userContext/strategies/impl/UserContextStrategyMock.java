package modules.userContext.strategies.impl;

import modules.music.structures.Genre;
import modules.userContext.strategies.core.UserContextStrategy;
import modules.userContext.structures.GenreTimeline;
import modules.userContext.structures.Location;
import modules.userContext.structures.TimelinePhase;
import modules.userContext.structures.UserContextDTO;

import java.time.LocalTime;
import java.util.List;

public class UserContextStrategyMock implements UserContextStrategy {
    private final UserContextDTO fixedContext;

    public UserContextStrategyMock() {
        // 1. Spiele 2 Min ROCK (davon in der letzten 1 Minute weicher Übergang zu EDM)
        // 2. Spiele 60 Min EDM (davon die letzten 15 Min weicher Übergang)
        // 3. Spiele unendlich lange POP
        var timeline = new GenreTimeline(List.of(
                new TimelinePhase(Genre.DEEP_HOUSE , 4.0, 1.0),
                new TimelinePhase(Genre.EDM, 1.0, 0.0),
                new TimelinePhase(Genre.POP, 120.0, 0.0)
        ));

        this.fixedContext = new UserContextDTO(
                105,
                Location.Bar,
                LocalTime.now(),
                timeline,
                120
        );
    }

    @Override
    public UserContextDTO getUserContext() {
        return fixedContext;
    }
}
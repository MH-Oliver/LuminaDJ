package modules.userContext.strategies.impl;

import modules.music.structures.Genre;
import modules.userContext.factories.TimelineFactory;
import modules.userContext.strategies.core.UserContextStrategy;
import modules.userContext.structures.*;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
@Service
public class UserContextStrategyMock implements UserContextStrategy {
    private final UserContextDTO fixedContext;

    public UserContextStrategyMock() {
        /*var customTimeline = new GenreTimeline(List.of(
                new TimelinePhase(Genre.EDM , 6.0, 1.0),
                new TimelinePhase(Genre.GERMAN, 6.0, 1.0),
                new TimelinePhase(Genre.EDM, 120.0, 0.0)
        ));*/

        var generatedTimeline = TimelineFactory.createTimelineForVibe(SessionVibe.WORKOUT);

        this.fixedContext = new UserContextDTO(
                105,
                Location.Bar,
                LocalTime.now(),
                generatedTimeline,
                500
        );
    }

    @Override
    public UserContextDTO getUserContext() {
        return fixedContext;
    }
}
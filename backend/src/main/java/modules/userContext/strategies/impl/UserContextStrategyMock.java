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
        

        var generatedTimeline = TimelineFactory.createTimelineForVibe(SessionVibe.WORKOUT);

        this.fixedContext = new UserContextDTO(
                105,
                Location.Bar,
                LocalTime.now(),
                generatedTimeline,
                500,
                120
        );
    }

    @Override
    public UserContextDTO getUserContext() {
        return fixedContext;
    }
}
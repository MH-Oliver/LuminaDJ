package modules.music.strategies.user_context;

import modules.music.strategies.core.UserContextStrategy;
import modules.music.structures.Location;
import modules.music.structures.UserContextDTO;

public class UserContextStrategyMock implements UserContextStrategy {
    @Override
    public UserContextDTO getUserContext() {
        var userContext = new UserContextDTO(
                105,
                Location.Bar
        );
        System.out.println("UserContext: " + userContext);
        return userContext;
    }
}

package modules.userContext.strategies.impl;

import modules.userContext.strategies.core.UserContextStrategy;
import modules.userContext.structures.Location;
import modules.userContext.structures.UserContextDTO;

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

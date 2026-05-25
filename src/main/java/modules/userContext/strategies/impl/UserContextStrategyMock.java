package modules.userContext.strategies.impl;

import modules.userContext.services.CurveGenerator;
import modules.userContext.strategies.core.UserContextStrategy;
import modules.userContext.structures.Location;
import modules.userContext.structures.UserContextDTO;

import java.time.LocalTime;
import java.util.Map;

public class UserContextStrategyMock implements UserContextStrategy {
    @Override
    public UserContextDTO getUserContext() {
        var energyCurve = CurveGenerator.createSmoothCurve(
                new double[] {20.0, 22.0, 24.0, 26.0},
                new double[] {0.5, 0.8, 1.0, 0.6}
        );
        var bpmCurve = CurveGenerator.createSmoothCurve(
                new double[] {20.0, 22.0, 24.0, 26.0},
                new double[] {110.0, 122.0, 128.0, 118.0}
        );

        var userContext = new UserContextDTO(
                105,
                Location.Bar,
                LocalTime.now(),
                Map.of(
                        "energy", energyCurve,
                        "bpm", bpmCurve
                )
        );
        System.out.println("UserContext: " + userContext);
        return userContext;
    }
}

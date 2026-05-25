package modules.userContext.structures;

import java.time.LocalTime;

/**
 * Functional interface for macro-level target curves over the evening timeline.
 */
@FunctionalInterface
public interface MacroCurve {
    double getTargetValueAt(LocalTime time);
}

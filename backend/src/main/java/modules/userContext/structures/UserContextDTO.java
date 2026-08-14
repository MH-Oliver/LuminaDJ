package modules.userContext.structures;

import java.time.LocalTime;
import java.util.Map;

public record UserContextDTO (
        int tempo,
        Location location,
        LocalTime startTime,
        GenreTimeline timeline,
        int songCooldownMinutes,
        int totalMinutes // NEU: Nimmt die Länge vom Frontend entgegen
) {}

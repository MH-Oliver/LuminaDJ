package modules.prediction;

import modules.music.strategies.music_source.LocalSongDatabaseAdapter;
import modules.music.structures.Genre;
import modules.music.structures.Track;
import modules.prediction.strategies.prediction.MacroCurveStrategy;
import modules.prediction.structures.PredictedAttributes;
import modules.prediction.structures.PredictionFactor;
import modules.userContext.strategies.core.UserContextStrategy;
import modules.userContext.structures.GenreTimeline;
import modules.userContext.structures.TimelinePhase;
import modules.userContext.structures.UserContextDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MacroCurveStrategyTest {

    @Test
    void testCalculate_ShouldReturnCorrectGenreWeightsAndEmptyFeatures() {
        // 1. Abhängigkeiten mocken
        LocalSongDatabaseAdapter fakeDb = mock(LocalSongDatabaseAdapter.class);
        when(fakeDb.getGenreCentroid("rock")).thenReturn(new PredictedAttributes(Map.of("energy", 0.8)));
        when(fakeDb.getGenreCentroid("pop")).thenReturn(new PredictedAttributes(Map.of("energy", 0.4)));

        // 2. Timeline konfigurieren (30 Min Rock, letzten 10 Min Übergang zu Pop)
        GenreTimeline timeline = new GenreTimeline(List.of(
                new TimelinePhase(Genre.ROCK, 30.0, 10.0),
                new TimelinePhase(Genre.POP, 60.0, 0.0)
        ));

        // 3. UserContext auf Minute 25 (Mitte des Übergangs) setzen
        LocalTime fakeStartTime = LocalTime.now().minusMinutes(25);
        UserContextDTO fakeContext = new UserContextDTO(100, null, fakeStartTime, timeline, 120);

        UserContextStrategy fakeContextStrategy = mock(UserContextStrategy.class);
        when(fakeContextStrategy.getUserContext()).thenReturn(fakeContext);

        MacroCurveStrategy strategy = new MacroCurveStrategy(fakeDb, fakeContextStrategy);
        Track currentTrack = new Track("id", "Test", "Artist", "rock", Map.of("energy", 0.6));

        // 4. Test ausführen
        PredictionFactor factor = strategy.calculate(currentTrack);

        // 5. Überprüfen: Gewichte müssen bei 50/50 liegen
        assertEquals(0.5, factor.genreWeights().get("rock"), 0.001);
        assertEquals(0.5, factor.genreWeights().get("pop"), 0.001);

        // 6. Überprüfen: Feature-Multiplikatoren müssen leer sein
        assertTrue(factor.features().isEmpty(), "Die Multiplikatoren-Map muss leer sein.");
    }
}
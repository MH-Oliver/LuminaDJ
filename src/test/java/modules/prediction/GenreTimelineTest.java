package modules.prediction;

import modules.music.structures.Genre;
import modules.userContext.structures.GenreTimeline;
import modules.userContext.structures.TimelinePhase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenreTimelineTest {

    @Test
    public void testGetWeightsAt_Phase1_NoTransition() {
        // Arrange (Vorbereiten)
        GenreTimeline timeline = new GenreTimeline(List.of(
                new TimelinePhase(Genre.ROCK, 30.0, 10.0),
                new TimelinePhase(Genre.EDM, 60.0, 0.0)
        ));

        Map<Genre, Double> weights = timeline.getWeightsAt(10.0);

        assertEquals(1, weights.size(), "Sollte nur ein Genre enthalten");
        assertEquals(1.0, weights.get(Genre.ROCK), 0.001, "Rock sollte 100% Gewicht haben");
    }

    @Test
    public void testGetWeightsAt_DuringTransition() {
        GenreTimeline timeline = new GenreTimeline(List.of(
                new TimelinePhase(Genre.ROCK, 30.0, 10.0), // Übergang startet bei Minute 20
                new TimelinePhase(Genre.EDM, 60.0, 0.0)
        ));

        Map<Genre, Double> weights = timeline.getWeightsAt(25.0);

        assertEquals(2, weights.size(), "Sollte in der Transition beide Genres mischen");
        assertEquals(0.5, weights.get(Genre.ROCK), 0.001, "Rock sollte auf 50% gefallen sein");
        assertEquals(0.5, weights.get(Genre.EDM), 0.001, "EDM sollte auf 50% gestiegen sein");
    }

    @Test
    public void testGetWeightsAt_AfterTimelineEnds_ShouldReturnLastGenre() {
        GenreTimeline timeline = new GenreTimeline(List.of(
                new TimelinePhase(Genre.ROCK, 30.0, 10.0),
                new TimelinePhase(Genre.POP, 30.0, 0.0)
        ));

        Map<Genre, Double> weights = timeline.getWeightsAt(100.0);

        assertEquals(1, weights.size());
        assertEquals(1.0, weights.get(Genre.POP), 0.001, "Sollte beim letzten Genre (POP) bleiben");
    }
}
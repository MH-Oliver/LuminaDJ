package modules.music;

import modules.music.strategies.music_source.LocalSongDatabaseAdapter;
import modules.music.structures.Track;
import modules.prediction.structures.PredictedAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LocalSongDatabaseAdapterTest {

    @TempDir
    Path tempDir;

    private LocalSongDatabaseAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        // 1. Eine kleine Fake-CSV-Datenbank erstellen
        File dummyCsv = tempDir.resolve("dummy_dataset.csv").toFile();
        try (FileWriter writer = new FileWriter(dummyCsv)) {
            // Header
            writer.write("0,track_id,artists,album_name,track_name,popularity,duration_ms,explicit,danceability,energy,key,loudness,mode,speechiness,acousticness,instrumentalness,liveness,valence,tempo,time_signature,track_genre\n");
            // Song 1: Rock, Energy 0.8, Artist: "RockBand"
            writer.write("1,\"id_rock1\",\"RockBand\",\"Album\",\"Rock Song 1\",50,200,False,0.5,0.8,1,-5,1,0.05,0.1,0.0,0.1,0.5,120.0,4,\"rock\"\n");
            // Song 2: EDM, Energy 0.9, Artist: "DJ Techno"
            writer.write("2,\"id_edm1\",\"DJ Techno\",\"Album\",\"EDM Banger\",50,200,False,0.8,0.9,1,-5,1,0.05,0.0,0.8,0.1,0.5,130.0,4,\"edm\"\n");
            // Song 3: Rock, Energy 0.4, Artist: "RockBand" (Gleicher Künstler wie Song 1!)
            writer.write("3,\"id_rock2\",\"RockBand\",\"Album\",\"Rock Ballad\",50,200,False,0.3,0.4,1,-5,1,0.05,0.8,0.0,0.1,0.3,90.0,4,\"rock\"\n");
        }

        // 2. Adapter mit der Fake-CSV initialisieren (HIER IST DIE ÄNDERUNG)
        adapter = new LocalSongDatabaseAdapter(dummyCsv.getAbsolutePath());
    }

    @Test
    void testCalculateCentroids_ShouldAverageFeaturesCorrectly() {
        // Act: Wir fragen den berechneten Schwerpunkt für "rock" ab.
        // Es gibt 2 Rock-Songs: Energy 0.8 und Energy 0.4. Der Durchschnitt MUSS 0.6 sein.
        PredictedAttributes rockCentroid = adapter.getGenreCentroid("rock");

        // Assert
        assertNotNull(rockCentroid);
        assertEquals(0.6, rockCentroid.features().get("energy"), 0.001, "Der Centroid-Durchschnitt für Energy ist falsch");
    }

    @Test
    void testGetTopK_ArtistCohesionBonus_ShouldPreferSameArtist() {
        // Arrange: Unser aktueller Song ist von "RockBand"
        Track currentTrack = new Track("id_current", "Current", "RockBand", "rock", Map.of("energy", 0.6));

        // Ziel-Vektor ist Energy 0.6.
        // In der DB sind Rock Song 1 (0.8) und Rock Ballad (0.4). Beide sind exakt 0.2 vom Ziel entfernt.
        // ABER: "Rock Ballad" ist vom selben Künstler ("RockBand") -> Artist Cohesion Bonus sollte greifen!
        PredictedAttributes target = new PredictedAttributes(Map.of("energy", 0.6));

        // Act
        List<Track> nextSongs = adapter.getTopK(target, currentTrack, 1);

        // Assert
        assertEquals(1, nextSongs.size());
        assertEquals("id_rock2", nextSongs.get(0).id(), "Artist-Bonus hat nicht funktioniert. Sollte Song vom selben Künstler bevorzugen.");
    }

    @Test
    void testGetTopK_GenreGravity_ShouldPenalizeWrongGenre() {
        // Arrange: Aktueller Song ist Rock.
        Track currentTrack = new Track("id_current", "Current", "RockBand", "rock", Map.of("energy", 0.85));

        // Ziel: Wir wollen GANZ DRINGEND zu EDM wechseln (Timeline gibt 100% EDM vor)
        PredictedAttributes target = new PredictedAttributes(
                Map.of("energy", 0.83),
                Map.of("edm", 1.0) // 100% EDM gewünscht!
        );

        // Act
        List<Track> nextSongs = adapter.getTopK(target, currentTrack, 1);

        // Assert
        // Obwohl "Rock Song 1" mit Energy 0.8 näher am Zielwert (0.85) ist als der "EDM Banger" (0.9),
        // MUSS der EDM-Song gewählt werden, weil Rock durch die Genre-Gravity eine massive Strafe bekommt.
        assertEquals("id_edm1", nextSongs.getFirst().id(), "Genre-Gravity hat nicht funktioniert. Es wurde nicht auf das Ziel-Genre gewechselt.");
    }

    @Test
    void testGetTopK_GenreGravity_ShouldChooseWrongGenreWhenOthersAreFarAway() {
        // Arrange: Aktueller Song ist Rock.
        Track currentTrack = new Track("id_current", "Current", "RockBand", "rock", Map.of("energy", 0.35));

        // Ziel: Wir wollen GANZ DRINGEND zu EDM wechseln (Timeline gibt 100% EDM vor)
        PredictedAttributes target = new PredictedAttributes(
                Map.of("energy", 0.36),
                Map.of("edm", 1.0) // 100% EDM gewünscht!
        );

        // Act
        List<Track> nextSongs = adapter.getTopK(target, currentTrack, 1);

        assertEquals("id_rock2", nextSongs.getFirst().id(), "Genre-Gravity zu stark. Es wurde fälschlicherweise auf das Ziel-Genre gewechselt.");
    }
}
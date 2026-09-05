package modules.music;

import com.typesafe.config.ConfigFactory;
import modules.music.repositories.PlayedSongRepository;
import modules.music.strategies.music_source.LocalSongDatabaseAdapter;
import modules.music.structures.Track;
import modules.prediction.structures.PredictedAttributes;
import modules.userContext.strategies.core.UserContextStrategy;
import modules.userContext.structures.UserContextDTO;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
            // Song 4: Ambient mit ähnlichen Features wie Techno
            writer.write("4,\"id_ambient1\",\"Calm Artist\",\"Album\",\"Ambient Drift\",50,200,False,0.6,0.85,1,-5,1,0.02,0.3,0.6,0.1,0.4,128.0,4,\"ambient\"\n");
            // Song 5: Techno mit ähnlichen Features wie Ambient
            writer.write("5,\"id_techno1\",\"Warehouse DJ\",\"Album\",\"Techno Pulse\",50,200,False,0.62,0.84,1,-5,1,0.02,0.28,0.62,0.1,0.42,128.0,4,\"techno\"\n");
            // Song 6: Zweiter Techno-Track für Top-K Prüfungen
            writer.write("6,\"id_techno2\",\"Warehouse DJ\",\"Album\",\"Night Driver\",50,200,False,0.58,0.82,1,-5,1,0.02,0.32,0.58,0.1,0.39,126.0,4,\"techno\"\n");
        }

        System.setProperty("songDatabase.path", dummyCsv.getAbsolutePath());
        ConfigFactory.invalidateCaches();

        PlayedSongRepository mockRepo = mock(PlayedSongRepository.class);
        when(mockRepo.isPlayable(anyString(), anyInt())).thenReturn(true);

        UserContextStrategy mockContext = mock(UserContextStrategy.class);
        when(mockContext.getUserContext()).thenReturn(
                new UserContextDTO(100, null, null, null, 60,60)
        );

        adapter = new LocalSongDatabaseAdapter(mockRepo, mockContext);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("songDatabase.path");
        ConfigFactory.invalidateCaches();
    }

    @Test
    void testCalculateCentroids_ShouldAverageFeaturesCorrectly() {
        PredictedAttributes rockCentroid = adapter.getGenreCentroid("rock");

        assertNotNull(rockCentroid);
        assertEquals(0.6, rockCentroid.features().get("energy"), 0.001, "Der Centroid-Durchschnitt für Energy ist falsch");
    }

    @Test
    void testGetTopK_ArtistCohesionBonus_ShouldPreferSameArtist() {
        Track currentTrack = new Track("id_current", "Current", "RockBand", "rock", Map.of("energy", 0.6));
        PredictedAttributes target = new PredictedAttributes(Map.of("energy", 0.6));

        List<Track> nextSongs = adapter.getTopK(target, currentTrack, 1);

        assertEquals(1, nextSongs.size());
        assertEquals("id_rock2", nextSongs.get(0).id(), "Artist-Bonus hat nicht funktioniert. Sollte Song vom selben Künstler bevorzugen.");
    }

    @Test
    void testGetTopK_GenreGravity_ShouldPenalizeWrongGenre() {
        Track currentTrack = new Track("id_current", "Current", "RockBand", "rock", Map.of("energy", 0.85));

        PredictedAttributes target = new PredictedAttributes(
                Map.of("energy", 0.83),
                Map.of("edm", 1.0)
        );

        List<Track> nextSongs = adapter.getTopK(target, currentTrack, 1);

        assertEquals("id_edm1", nextSongs.getFirst().id(), "Genre-Gravity hat nicht funktioniert. Es wurde nicht auf das Ziel-Genre gewechselt.");
    }

    @Test
    void testGetTopK_StrictGenreFilter_ShouldReturnOnlyTargetGenreWhenPrioritizedFeaturesDiffer() {
        Track currentTrack = new Track("id_current", "Current", "Calm Artist", "ambient", Map.of("energy", 0.84, "danceability", 0.6, "bpm", 0.64));

        PredictedAttributes target = new PredictedAttributes(
                Map.of("energy", 0.84, "danceability", 0.6, "bpm", 0.64),
                Map.of("techno", 1.0)
        );

        List<Track> nextSongs = adapter.getTopK(target, currentTrack, 2);

        assertEquals(2, nextSongs.size());
        for (Track track : nextSongs) {
            assertEquals("techno", track.genre(), "Bei Genre-Wechsel dürfen nur Tracks des Ziel-Genres empfohlen werden.");
        }
    }

    /*@Test
    void testGetTopK_GenreGravity_ShouldChooseWrongGenreWhenOthersAreFarAway() {
        Track currentTrack = new Track("id_current", "Current", "RockBand", "rock", Map.of("energy", 0.35));

        PredictedAttributes target = new PredictedAttributes(
                Map.of("energy", 0.36),
                Map.of("edm", 1.0)
        );

        List<Track> nextSongs = adapter.getTopK(target, currentTrack, 1);

        assertEquals("id_rock2", nextSongs.getFirst().id(), "Genre-Gravity zu stark. Es wurde fälschlicherweise auf das Ziel-Genre gewechselt.");
    }*/
}
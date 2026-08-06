package modules.music;

import com.typesafe.config.ConfigFactory;
import modules.music.repositories.PlayedSongRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayedSongRepositoryTest {

    @TempDir
    Path tempDir;

    private String testHistoryPath;

    @BeforeEach
    void setUp() {
        testHistoryPath = tempDir.resolve("test_played_history.csv").toString();

        System.setProperty("playedSongs.path", testHistoryPath);
        ConfigFactory.invalidateCaches();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("playedSongs.path");
        ConfigFactory.invalidateCaches();
    }

    @Test
    void testIsPlayable_NewSong_ShouldReturnTrue() {
        PlayedSongRepository repo = new PlayedSongRepository();

        // Ein noch nie gespielter Song sollte spielbar sein
        assertTrue(repo.isPlayable("song123", 60), "Ein ungespielter Song muss spielbar sein.");
    }

    @Test
    void testMarkAsPlayed_ShouldBlockSongDuringCooldown() {
        PlayedSongRepository repo = new PlayedSongRepository();

        repo.markAsPlayed("song123");

        // Unmittelbar nach dem Abspielen sollte er für 60 Minuten geblockt sein
        assertFalse(repo.isPlayable("song123", 60), "Song sollte direkt nach dem Spielen blockiert sein.");

        // Wenn der Cooldown 0 ist, darf er sofort wieder gespielt werden
        assertTrue(repo.isPlayable("song123", 0), "Song sollte spielbar sein, wenn der Cooldown 0 ist.");
    }

    @Test
    void testResetHistory_ShouldClearAllEntries() {
        PlayedSongRepository repo = new PlayedSongRepository();

        repo.markAsPlayed("song123");
        repo.resetHistory();

        // Nach dem Reset darf der Song wieder gespielt werden
        assertTrue(repo.isPlayable("song123", 60), "Song sollte nach dem Reset wieder freigegeben sein.");
    }

    @Test
    void testPersistence_ShouldLoadPreviousStateFromFile() {
        // 1. Wir speichern einen Song mit der ersten Instanz (schreibt ihn ins TempDir)
        PlayedSongRepository repo1 = new PlayedSongRepository();
        repo1.markAsPlayed("song123");

        // 2. Wir "simulieren" einen modules.App-Neustart, indem wir eine neue Instanz für dieselbe Datei erzeugen
        PlayedSongRepository repo2 = new PlayedSongRepository();

        // 3. Die neue Instanz muss die Daten aus der CSV gelesen haben und den Song weiterhin blockieren
        assertFalse(repo2.isPlayable("song123", 60), "Die zweite Instanz hat die Historie nicht aus der CSV geladen.");
    }
}
import modules.music.services.MusicService;
import modules.music.strategies.core.MusicPlayerAdapter;
import modules.music.strategies.music_player.MusicPlayerAdapterMock;
import modules.music.strategies.music_player.spotify.SpotifyAdapter;
import modules.music.strategies.music_player.spotify.SpotifyAuthenticator;
import modules.music.strategies.song_selector.SongSelectorMock;
import modules.music.strategies.user_context.UserContextStrategyMock;
import modules.music.structures.Track;
import modules.vision.services.VisionService;
import modules.vision.strategies.detection.DetectionStrategyMock;
import se.michaelthelin.spotify.SpotifyApi;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class App
{
    public static void main( String[] args ) throws IOException {


        //Spotify Authentifizierung
        SpotifyAuthenticator authenticator = new SpotifyAuthenticator();
        SpotifyApi spotifyApi = authenticator.authenticate();

        if (spotifyApi == null) {
            System.out.println("App wird beendet, da Spotify-Login fehlgeschlagen ist.");
            return;
        }

        VisionService visionService = new VisionService(new DetectionStrategyMock());

        MusicPlayerAdapter spotifyPlayer = new SpotifyAdapter(spotifyApi);

        MusicService musicService = new MusicService(
                spotifyPlayer,
                new SongSelectorMock(),
                new UserContextStrategyMock()
        );

        BufferedImage img = ImageIO.read(new File("src/main/resources/testScene.png"));

        System.out.println("\n---- Starte normale Verarbeitung --- ");

        //spielt "In the End" ab (über den SongSelectorMock)
        musicService.handleFrame(visionService.processFrame(img));

        // --- SIMULATION FÜR DEN ZWEITEN SONG ---
        System.out.println("\nLasse Song 1 für 5 Sekunden laufen...");
        try {
            Thread.sleep(5000); // 5 Sekunden warten
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("\n---- Füge jetzt zweiten Song hinzu --- ");
        // Neuer Song, der nach dem aktuellen gespielt werden soll
        Track secondTrack = new Track("Numb", "Linkin Park", "2nLtzopw4rPReszdYBJU6h");

        //Aufruf über den Adapter
        spotifyPlayer.addToQueue(secondTrack);

        System.out.println("Simulation abgeschlossen!");
    }
}


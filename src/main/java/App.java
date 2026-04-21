import modules.music.services.MusicService;
import modules.music.strategies.music_player.MusicPlayerAdapterMock;
import modules.music.strategies.music_player.spotify.SpotifyAdapter;
import modules.music.strategies.music_player.spotify.SpotifyAuthenticator;
import modules.music.strategies.song_selector.SongSelectorMock;
import modules.music.strategies.user_context.UserContextStrategyMock;
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


        // Spotify feature
        // 1. Spotify Authentifizierung durchführen
        SpotifyAuthenticator authenticator = new SpotifyAuthenticator();
        SpotifyApi spotifyApi = authenticator.authenticate();

        if (spotifyApi == null) {
            System.out.println("App wird beendet, da Spotify-Login fehlgeschlagen ist.");
            return;
        }

        VisionService visionService = new VisionService(new DetectionStrategyMock());
        MusicService musicService = new MusicService(
                new SpotifyAdapter(spotifyApi),
                new SongSelectorMock(),
                new UserContextStrategyMock()
        );




        // Test

        BufferedImage img = ImageIO.read(new File("src/main/resources/testScene.png"));

        System.out.println("---- Starte Verarbeitung --- ");

        musicService.handleFrame(
                visionService.processFrame(img)
        );
    }
}

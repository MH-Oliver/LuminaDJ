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
        VisionService visionService = new VisionService(new DetectionStrategyMock());

        MusicPlayerAdapter spotifyPlayer = new SpotifyAdapter();

        MusicService musicService = new MusicService(
                spotifyPlayer,
                new SongSelectorMock(),
                new UserContextStrategyMock()
        );

        BufferedImage img = ImageIO.read(new File("src/main/resources/testScene.png"));

        System.out.println("\n---- Starte normale Verarbeitung --- ");
        musicService.handleFrame(visionService.processFrame(img));
    }
}


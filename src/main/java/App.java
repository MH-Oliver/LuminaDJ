import modules.music.services.MusicService;
import modules.music.strategies.music_player.MusicPlayerAdapterMock;
import modules.music.strategies.song_selector.SongSelectorMock;
import modules.music.strategies.user_context.UserContextStrategyMock;
import modules.vision.services.VisionService;
import modules.vision.strategies.detection.DetectionStrategyMock;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class App
{
    public static void main( String[] args ) throws IOException {
        VisionService visionService = new VisionService(new DetectionStrategyMock());
        MusicService musicService = new MusicService(
                new MusicPlayerAdapterMock(),
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

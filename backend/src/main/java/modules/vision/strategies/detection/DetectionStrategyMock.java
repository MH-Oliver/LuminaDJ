package modules.vision.strategies.detection;

import modules.vision.strategies.core.DetectionStrategy;
import modules.vision.structures.FrameDataDTO;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;

@Service
public class DetectionStrategyMock implements DetectionStrategy {
    @Override
    public FrameDataDTO analyse(BufferedImage image) {
        var frameData = new FrameDataDTO(50, 0);

        System.out.println("MOCK: Erkannte Frame-Data im Bild: " + frameData);

        return frameData;
    }
}

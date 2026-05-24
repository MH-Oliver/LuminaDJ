package modules.vision.strategies.detection;

import modules.userContext.structures.UserContextDTO;
import modules.vision.strategies.core.DetectionStrategy;
import modules.vision.structures.Emotion;
import modules.vision.structures.FrameDataDTO;

import java.awt.image.BufferedImage;

public class DetectionStrategyMock implements DetectionStrategy {
    @Override
    public FrameDataDTO analyse(BufferedImage image) {
        var frameData = new FrameDataDTO(-1, -1);

        System.out.println("MOCK: Erkannte Frame-Data im Bild: " + frameData);

        return frameData;
    }
}

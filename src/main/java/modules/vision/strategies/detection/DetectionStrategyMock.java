package modules.vision.strategies.detection;

import modules.vision.strategies.core.DetectionStrategy;
import modules.vision.structures.Emotion;
import modules.vision.structures.FrameDataDTO;

import java.awt.image.BufferedImage;

public class DetectionStrategyMock implements DetectionStrategy {
    @Override
    public FrameDataDTO analyse(BufferedImage image) {
        var frameData = new FrameDataDTO(11, Emotion.Anger);

        System.out.println("Erkannte Frame-Data im Bild: " + frameData);

        return new FrameDataDTO(3, Emotion.Happy);
    }
}

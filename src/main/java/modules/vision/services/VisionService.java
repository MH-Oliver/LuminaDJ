package modules.vision.services;

import modules.music.strategies.core.UserContextStrategy;
import modules.music.strategies.user_context.UserContextStrategyMock;
import modules.vision.strategies.core.DetectionStrategy;
import modules.vision.structures.FrameDataDTO;

import java.awt.image.BufferedImage;

public class VisionService {

    private DetectionStrategy detectionStrategy;
    private UserContextStrategy userContextStrategy;

    public VisionService(DetectionStrategy detectionStrategy, UserContextStrategyMock userContextStrategy) {
        this.detectionStrategy = detectionStrategy;
        this.userContextStrategy = userContextStrategy;
    }

    public FrameDataDTO processFrame(BufferedImage image) {

        return detectionStrategy.analyse(image, userContextStrategy.getUserContext());
    }
}

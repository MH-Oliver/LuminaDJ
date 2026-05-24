package modules.vision.services;

import modules.userContext.strategies.core.UserContextStrategy;
import modules.vision.strategies.core.DetectionStrategy;
import modules.vision.structures.FrameDataDTO;

import java.awt.image.BufferedImage;

public class VisionService {

    private DetectionStrategy detectionStrategy;
    private UserContextStrategy userContextStrategy;

    public VisionService(DetectionStrategy detectionStrategy) {
        this.detectionStrategy = detectionStrategy;
    }

    public FrameDataDTO processFrame(BufferedImage image) {

        return detectionStrategy.analyse(image);
    }
}

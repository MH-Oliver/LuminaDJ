package modules.vision.services;

import modules.vision.strategies.core.DetectionStrategy;
import modules.vision.structures.FrameDataDTO;

import java.awt.image.BufferedImage;

public class VisionService {

    private DetectionStrategy detectionStrategy;

    public VisionService(DetectionStrategy detectionStrategy) {
        this.detectionStrategy = detectionStrategy;
    }

    public FrameDataDTO processFrame(BufferedImage image) {

        return detectionStrategy.analyse(image);
    }
}

package modules.vision.strategies.core;

import modules.vision.structures.FrameDataDTO;

import java.awt.image.BufferedImage;

public interface DetectionStrategy {
    FrameDataDTO analyse(BufferedImage image);
}

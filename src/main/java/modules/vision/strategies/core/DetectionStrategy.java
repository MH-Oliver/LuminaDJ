package modules.vision.strategies.core;

import modules.userContext.structures.UserContextDTO;
import modules.vision.structures.FrameDataDTO;

import java.awt.image.BufferedImage;

public interface DetectionStrategy {
    FrameDataDTO analyse(BufferedImage image);
}

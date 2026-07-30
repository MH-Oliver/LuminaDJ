package modules.music.structures;

import modules.userContext.structures.UserContextDTO;
import modules.vision.structures.FrameDataDTO;

public record DataBundle(
        FrameDataDTO dynamicData,
        UserContextDTO staticData
) {
}

package modules.music.strategies.core;

import modules.music.structures.DataBundle;
import modules.music.structures.Track;

public interface SongSelectorStrategy {
    Track selectTrack(DataBundle dataBundle);
}

package modules.music.strategies.song_selector;

import modules.music.strategies.core.SongSelectorStrategy;
import modules.music.structures.DataBundle;
import modules.music.structures.Track;

public class SongSelectorMock implements SongSelectorStrategy {
    @Override
    public Track selectTrack(DataBundle dataBundle) {
        return new Track("In the End", "Linkin Park", "60a0Rd6pjrkxjPbaKzXjfq");
    }
}

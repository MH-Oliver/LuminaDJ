package modules.music.services;

import modules.music.strategies.core.MusicPlayerAdapter;
import modules.music.strategies.core.SongSelectorStrategy;
import modules.music.strategies.core.UserContextStrategy;
import modules.music.structures.DataBundle;
import modules.music.structures.Track;
import modules.music.structures.UserContextDTO;
import modules.vision.structures.FrameDataDTO;

public class MusicService {

    private MusicPlayerAdapter musicPlayer;
    private SongSelectorStrategy selectionStrategy;
    private UserContextStrategy userContextStrategy;

    public MusicService(MusicPlayerAdapter musicPlayer, SongSelectorStrategy selectionStrategy, UserContextStrategy userContextStrategy) {
        this.musicPlayer = musicPlayer;
        this.selectionStrategy = selectionStrategy;
        this.userContextStrategy = userContextStrategy;
    }

    public void handleFrame(FrameDataDTO frameData) {
        var dataBundle = new DataBundle(
                frameData,
                userContextStrategy.getUserContext()
        );

        Track track = selectionStrategy.selectTrack(dataBundle);

        musicPlayer.play(track);
    }
}

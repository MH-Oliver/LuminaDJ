package modules.music.strategies.music_player;

import modules.music.strategies.core.MusicPlayerAdapter;
import modules.music.structures.Track;

public class MusicPlayerAdapterMock implements MusicPlayerAdapter {
    @Override
    public void play(Track track) {
        System.out.println("Lautsprecher spielt: " + track);
    }

    @Override
    public void pause() {

    }

    @Override
    public void setVolume(int level) {

    }

    @Override
    public long getPlaybackPosition() {
        return 0;
    }

    // NEU
    @Override
    public void addToQueue(Track track) {
        System.out.println("Mock reiht in Warteschlange ein: " + track.name());
    }
}

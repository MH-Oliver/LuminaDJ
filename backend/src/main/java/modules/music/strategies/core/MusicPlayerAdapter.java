package modules.music.strategies.core;

import modules.music.structures.Track;

public interface MusicPlayerAdapter {
    void play(Track track);
    void pause();
    void resume();
    void seek(long positionMs);
    boolean isPlaying();
    void setVolume(int level);
    long getPlaybackPosition();
    void addToQueue(Track track);
    void skip();
    void stop();
    void setTrackFavoriteStatus(Track track, boolean isFavorite);
}
package modules.music.strategies.core;

import modules.music.structures.Track;

public interface MusicPlayerAdapter {
    void play(Track track);
    void pause();
    void resume(); // NEU
    void seek(long positionMs); // NEU
    boolean isPlaying(); // NEU
    void setVolume(int level);
    long getPlaybackPosition();
    void addToQueue(Track track);
    void skip();
    void stop(); // NEU
    void setTrackFavoriteStatus(Track track, boolean isFavorite);
}
package modules.music.strategies.core;

import modules.music.structures.Track;

public interface MusicPlayerAdapter {
    void play(Track track);
    void pause();
    void setVolume(int level);
    long getPlaybackPosition();
    // NEU: Methode für die Warteschlange
    void addToQueue(Track track);
}

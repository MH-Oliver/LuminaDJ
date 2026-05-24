package modules.music.strategies.music_player;

import modules.music.strategies.core.MusicPlayerAdapter;
import modules.music.structures.Track;

public class MusicPlayerAdapterMock implements MusicPlayerAdapter {
    @Override
    public void play(Track track) {
        System.out.println("Mock Player: Lade und spiele Song ab -> " + track.name());
        System.out.println("Mock Player: Blockiere System für 10 Sekunden (Playback-Simulation läuft)...");

        try {
            Thread.sleep(60000);
        } catch (InterruptedException e) {
            System.err.println("Mock Player: Playback wurde unerwartet unterbrochen!");
            Thread.currentThread().interrupt();
        }

        System.out.println("Mock Player: Song " + track.name() + " ist regulär beendet.");
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

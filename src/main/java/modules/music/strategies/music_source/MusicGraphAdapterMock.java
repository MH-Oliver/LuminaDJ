package modules.music.strategies.music_source;

import modules.music.strategies.core.MusicSourceAdapter;
import modules.music.structures.Track;
import modules.prediction.structures.PredictedAttributes;

public class MusicGraphAdapterMock implements MusicSourceAdapter {
    @Override
    public Track getNextSong(PredictedAttributes target, Track currentSong) {
        System.out.println("Mock Music-Graph: Suche Song nahe Energy " + target.energy() + " und BPM " + target.bpm());

        // Liefert einfach einen Dummy-Song mit den perfekten Zielwerten zurück
        var newTrack = new Track("3K4HG9evC7dg3N0R9cYqk4", "One Step Closer", "Linkin Park",
                target.energy(), target.bpm(), target.danceability(), target.acousticness(),
                target.instrumentalness(), target.speechiness());
        System.out.println("Mock Music-Graph: Neuer Track -> " + newTrack);
        return newTrack;
    }
}

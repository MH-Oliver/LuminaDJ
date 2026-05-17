package modules.music.structures;

public record Track(
        String id,
        String name,
        String author,
        double energy,
        double bpm
){
}

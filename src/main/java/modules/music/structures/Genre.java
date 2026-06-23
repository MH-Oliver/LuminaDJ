package modules.music.structures;

public enum Genre {
    HIP_HOP("hip-hop", "6AI3ezQ4o3HUoP6Dhudph3"),
    POP("pop", "7qiZfU4dY1lWllzX7mPBI3"),
    ROCK("rock", "2zYzyRzz6pRmhPzyfMEC8s"),
    COUNTRY("country", "1QbOvACeYanja5pbnJbAmk"),
    EDM("edm", "6gdDu39yYqPcaTgCwYEW8i"),
    LATIN("latin", "6habFhsOp2NvshLv26DqMb"),
    K_POP("k-pop", "1CPZ5BxNNd0n0nF4Orb9JS"),
    RNB_SOUL("r-n-b", "0I3q5fE6wg7LIfHGngUTnV"),
    JAZZ("jazz", "43iIQbw5hx986dUEZbr3eN"),
    CLASSICAL("classical", "17mTPR6CmBQu8AsgBRPsw4"),

    ACOUSTIC("acoustic", ""), AFROBEAT("afrobeat", ""), ALT_ROCK("alt-rock", ""), ALTERNATIVE("alternative", ""),
    AMBIENT("ambient", ""), ANIME("anime", ""), BLACK_METAL("black-metal", ""), BLUEGRASS("bluegrass", ""),
    BLUES("blues", ""), BOSSANOVA("bossanova", ""), BRAZIL("brazil", ""), BREAKBEAT("breakbeat", ""),
    BRITISH("british", ""), CANTOPOP("cantopop", ""), CHICAGO_HOUSE("chicago-house", ""), CHILDREN("children", ""),
    CHILL("chill", ""), CLUB("club", ""), COMEDY("comedy", ""), DANCEHALL("dancehall", ""),
    DEATH_METAL("death-metal", ""), DEEP_HOUSE("deep-house", ""), DETROIT_TECHNO("detroit-techno", ""), DISCO("disco", ""),
    DISNEY("disney", ""), DRUM_AND_BASS("drum-and-bass", ""), DUB("dub", ""), DUBSTEP("dubstep", ""),
    ELECTRO("electro", ""), ELECTRONIC("electronic", ""), EMO("emo", ""), FOLK("folk", ""),
    FORRO("forro", ""), FRENCH("french", ""), FUNK("funk", ""), GARAGE("garage", ""),
    GERMAN("german", ""), GOSPEL("gospel", ""), GOTH("goth", ""), GRINDCORE("grindcore", ""),
    GROOVE("groove", ""), GRUNGE("grunge", ""), GUITAR("guitar", ""), HAPPY("happy", ""),
    HARD_ROCK("hard-rock", ""), HARDCORE("hardcore", ""), HARDSTYLE("hardstyle", ""), HEAVY_METAL("heavy-metal", ""),
    HOLIDAYS("holidays", ""), HONKY_TONK("honky-tonk", ""), HOUSE("house", ""), IDM("idm", ""),
    INDIAN("indian", ""), INDIE("indie", ""), INDIE_POP("indie-pop", ""), INDUSTRIAL("industrial", ""),
    IRANIAN("iranian", ""), J_DANCE("j-dance", ""), J_IDOL("j-idol", ""), J_POP("j-pop", ""),
    J_ROCK("j-rock", ""), KIDS("kids", ""), LATINO("latino", ""), MALAY("malay", ""),
    MANDOPOP("mandopop", ""), METAL("metal", ""), METAL_MISC("metal-misc", ""), METALCORE("metalcore", ""),
    MINIMAL_TECHNO("minimal-techno", ""), MOVIES("movies", ""), MPB("mpb", ""), NEW_AGE("new-age", ""),
    NEW_RELEASE("new-release", ""), OPERA("opera", ""), PAGODE("pagode", ""), PARTY("party", ""),
    PIANO("piano", ""), POP_FILM("pop-film", ""), POST_DUBSTEP("post-dubstep", ""), POWER_POP("power-pop", ""),
    PROGRESSIVE_HOUSE("progressive-house", ""), PSYCH_ROCK("psych-rock", ""), PUNK("punk", ""), PUNK_ROCK("punk-rock", ""),
    RAIN("rain", ""), REGGAE("reggae", ""), REGGAETON("reggaeton", ""), ROAD_TRIP("road-trip", ""),
    ROCK_N_ROLL("rock-n-roll", ""), ROCKABILLY("rockabilly", ""), ROMANCE("romance", ""), SAD("sad", ""),
    SALSA("salsa", ""), SAMBA("samba", ""), SERTANEJO("sertanejo", ""), SHOW_TUNES("show-tunes", ""),
    SINGER_SONGWRITER("singer-songwriter", ""), SKA("ska", ""), SLEEP("sleep", ""), SONGWRITER("songwriter", ""),
    SOUL("soul", ""), SOUNDTRACKS("soundtracks", ""), SPANISH("spanish", ""), STUDY("study", ""),
    SUMMER("summer", ""), SWEDISH("swedish", ""), SYNTH_POP("synth-pop", ""), TANGO("tango", ""),
    TECHNO("techno", ""), TRANCE("trance", ""), TRIP_HOP("trip-hop", ""), TURKISH("turkish", ""),
    WORLD_MUSIC("world-music", "");

    private final String displayName;
    private final String seedTrackId;

    Genre(String displayName, String seedTrackId) {
        this.displayName = displayName;
        this.seedTrackId = seedTrackId;
    }

    public String getDisplayName() { return displayName; }
    public String getSeedTrackId() { return seedTrackId; }

    public static Genre fromString(String text) {
        for (Genre genre : Genre.values()) {
            if (genre.displayName.equalsIgnoreCase(text)) {
                return genre;
            }
        }
        return POP;
    }
}
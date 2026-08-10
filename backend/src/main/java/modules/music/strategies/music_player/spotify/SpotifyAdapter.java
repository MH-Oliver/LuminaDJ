package modules.music.strategies.music_player.spotify;

import com.google.gson.JsonArray;
import modules.music.strategies.core.MusicPlayerAdapter;
import modules.music.structures.Track;
import org.springframework.stereotype.Service;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.requests.data.player.PauseUsersPlaybackRequest;
import se.michaelthelin.spotify.requests.data.player.SetVolumeForUsersPlaybackRequest;
import se.michaelthelin.spotify.requests.data.player.StartResumeUsersPlaybackRequest;
import se.michaelthelin.spotify.requests.data.player.SkipUsersPlaybackToNextTrackRequest;
@Service
public class SpotifyAdapter implements MusicPlayerAdapter {

    public static SpotifyApi spotifyApi;

    // NEU: Variablen für das Threading (ähnlich wie SmartphoneKameraStrategy)
    private volatile boolean isRunning = false;
    private Thread playbackThread;
    private final int POLL_INTERVAL_MS = 3000; // API alle 3 Sekunden nach dem Status fragen

    /**
     * Erstellt einen neuen Adapter mit einer bereits authentifizierten API-Instanz.
     */
    public SpotifyAdapter() {
        SpotifyAuthenticator authenticator = new SpotifyAuthenticator();
        SpotifyApi spotifyApi = authenticator.authenticate();

        if (spotifyApi == null) {
            throw new IllegalArgumentException("modules.App wird beendet, da Spotify-Login fehlgeschlagen ist.");
        }

        SpotifyAdapter.spotifyApi = spotifyApi;
    }

    @Override
    public void play(Track track) {
        try {
            String trackUri = track.id().startsWith("spotify:track:") ? track.id() : "spotify:track:" + track.id();

            // KORREKTUR: Normales Instanziieren statt Double-Brace Initialization
            com.google.gson.JsonArray uris = new com.google.gson.JsonArray();
            uris.add(trackUri);

            synchronized (spotifyApi) {
                spotifyApi.startResumeUsersPlayback()
                        .uris(uris)
                        .build().execute();
            }
            System.out.println("Spotify spielt jetzt: " + track.name());

            isRunning = true;
            playbackThread = new Thread(() -> {
                try { Thread.sleep(3000); } catch (InterruptedException e) { return; }

                while (isRunning) {
                    try {
                        CurrentlyPlayingContext context;
                        synchronized (spotifyApi) {
                            context = spotifyApi.getInformationAboutUsersCurrentPlayback().build().execute();
                        }

                        if (context != null && context.getItem() != null) {
                            String currentPlayingId = context.getItem().getId();
                            Integer progressMs = context.getProgress_ms();
                            // Sicheres Auslesen der Track-Dauer
                            Integer durationMs = null;
                            if (context.getItem() instanceof se.michaelthelin.spotify.model_objects.specification.Track t) {
                                durationMs = t.getDurationMs();
                            }

                            // 1. Manuell übersprungen? (In der Spotify App auf Handy)
                            if (currentPlayingId != null && !currentPlayingId.equals(track.id())) {
                                isRunning = false;
                            }
                            // 2. Reguläres Ende? (NUR prüfen, wenn er gerade wirklich spielt)
                            else if (context.getIs_playing() && durationMs != null && progressMs != null) {
                                if ((durationMs - progressMs) <= POLL_INTERVAL_MS) {
                                    System.out.println("Song nähert sich dem natürlichen Ende.");
                                    isRunning = false;
                                }
                            }
                            // Wenn is_playing == false (Pausiert), tun wir NICHTS. Die Schleife wartet einfach.
                        }

                        if (isRunning) Thread.sleep(POLL_INTERVAL_MS);

                    } catch (InterruptedException e) {
                        break; // Thread soll beendet werden
                    } catch (Exception e) {
                        try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ie) { break; }
                    }
                }
            });
            playbackThread.start();
            playbackThread.join(); // Blockiert den DjController sauber, bis isRunning false wird
            System.out.println("Play-Methode für " + track.name() + " regulär beendet.");
        } catch (Exception e) {
            handleError("Fehler beim Starten der Wiedergabe", e);
        }
    }

    @Override
    public void pause() {
        try {
            // WICHTIG: isRunning bleibt true! Wir pausieren nur Spotify.
            synchronized (spotifyApi) {
                spotifyApi.pauseUsersPlayback().build().execute();
            }
        } catch (Exception e) { handleError("Fehler beim Pausieren", e); }
    }


    @Override
    public void resume() {
        try {
            synchronized (spotifyApi) {
                spotifyApi.startResumeUsersPlayback().build().execute();
            }
        } catch (Exception e) { handleError("Fehler beim Fortsetzen", e); }
    }


    @Override
    public void seek(long positionMs) {
        try {
            synchronized (spotifyApi) {
                spotifyApi.seekToPositionInCurrentlyPlayingTrack((int) positionMs).build().execute();
            }
        } catch (Exception e) { handleError("Fehler beim Spulen", e); }
    }

    @Override
    public void skip() {
        // Hier beenden wir den Thread, damit der DjController aus dem .join() befreit wird
        // und automatisch den neuen Song sucht! (Wir rufen NICHT extra die Spotify Skip-API auf).
        System.out.println("Skip ausgelöst - Lade neuen Track...");
        isRunning = false;
        if (playbackThread != null) {
            playbackThread.interrupt();
        }
    }

    @Override
    public boolean isPlaying() {
        try {
            synchronized (spotifyApi) {
                CurrentlyPlayingContext ctx = spotifyApi.getInformationAboutUsersCurrentPlayback().build().execute();
                return ctx != null && ctx.getIs_playing();
            }
        } catch (Exception e) {
            return false;
        }
    }



    @Override
    public void setVolume(int level) {
        try {
            int volume = Math.max(0, Math.min(100, level));

            SetVolumeForUsersPlaybackRequest volumeRequest = spotifyApi
                    .setVolumeForUsersPlayback(volume)
                    .build();

            volumeRequest.execute();
            System.out.println("Lautstärke auf " + volume + "% gesetzt.");
        } catch (Exception e) {
            handleError("Fehler beim Setzen der Lautstärke", e);
        }
    }

    @Override
    public long getPlaybackPosition() {
        try {
            synchronized (spotifyApi) {
                CurrentlyPlayingContext ctx = spotifyApi.getInformationAboutUsersCurrentPlayback().build().execute();
                return (ctx != null && ctx.getProgress_ms() != null) ? ctx.getProgress_ms() : 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void stop() {
        try {
            System.out.println("Spotify: Session wird gestoppt...");
            isRunning = false;
            if (playbackThread != null) {
                playbackThread.interrupt();
            }
            synchronized (spotifyApi) {
                spotifyApi.pauseUsersPlayback().build().execute();
            }
        } catch (Exception e) {
            // Ignorieren, falls Spotify bereits pausiert ist
            System.err.println("Spotify war beim Stoppen bereits pausiert.");
        }
    }


    @Override
    public void addToQueue(Track track) {
        try {
            String trackUri = track.id().startsWith("spotify:track:")
                    ? track.id()
                    : "spotify:track:" + track.id();

            spotifyApi.addItemToUsersPlaybackQueue(trackUri).build().execute();

            System.out.println("Song zur Warteschlange hinzugefügt: " + track.name() + " (" + track.author() + ")");
        } catch (Exception e) {
            handleError("Fehler beim Hinzufügen zur Warteschlange", e);
        }
    }

    private void handleError(String message, Exception e) {
        System.err.println(message + ": " + e.getMessage());
    }


    @Override
    public void setTrackFavoriteStatus(Track track, boolean isFavorite) {
        try {
            String pureId = track.id().replace("spotify:track:", "");

            // NEU: Thread-Sicherheit hergestellt
            synchronized (spotifyApi) {
                if (isFavorite) {
                    spotifyApi.saveTracksForUser(pureId).build().execute();
                    System.out.println("Spotify: Song zu 'Lieblingssongs' hinzugefügt: " + track.name());
                } else {
                    spotifyApi.removeUsersSavedTracks(pureId).build().execute();
                    System.out.println("Spotify: Song aus 'Lieblingssongs' entfernt: " + track.name());
                }
            }
        } catch (Exception e) {
            handleError("Fehler beim Ändern des Favoriten-Status", e);
        }
    }
}
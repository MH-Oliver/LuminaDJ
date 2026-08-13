// modules/music/strategies/music_player/spotify/SpotifyAdapter.java
package modules.music.strategies.music_player.spotify;

import modules.music.strategies.core.MusicPlayerAdapter;
import modules.music.structures.Track;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.model_objects.miscellaneous.Device;
import se.michaelthelin.spotify.requests.data.player.SetVolumeForUsersPlaybackRequest;

import java.awt.Desktop;
import java.net.URI;

public class SpotifyAdapter implements MusicPlayerAdapter {

    public static SpotifyApi spotifyApi;
    private volatile boolean isRunning = false;
    private Thread playbackThread;
    private final int POLL_INTERVAL_MS = 3000;

    public SpotifyAdapter(SpotifyAuthenticator authenticator) {
        SpotifyAdapter.spotifyApi = authenticator.getSpotifyApi();
    }

    @Override
    public void play(Track track) {
        try {
            // 1. Suche nach verfügbaren Geräten (auch inaktiven)
            Device[] devices;
            synchronized (spotifyApi) {
                devices = spotifyApi.getUsersAvailableDevices().build().execute();
            }

            String targetDeviceId = null;
            boolean isActiveDevicePresent = false;

            if (devices.length > 0) {
                // Prüfen, ob schon ein Gerät aktiv ist
                for (Device d : devices) {
                    if (d.getIs_active()) {
                        isActiveDevicePresent = true;
                        targetDeviceId = d.getId();
                        break;
                    }
                }
                // Wenn keins aktiv ist, nehmen wir einfach das erste (meist der PC) um es aufzuwecken!
                if (!isActiveDevicePresent) {
                    targetDeviceId = devices[0].getId();
                }
            } else {
                // 2. Spotify ist komplett geschlossen. Wir zwingen das Betriebssystem, es zu öffnen.
                System.out.println("Spotify: Kein Gerät gefunden. Versuche Spotify automatisch zu starten...");
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    // Der spotify: Link öffnet die Desktop-App auf Windows/Mac
                    Desktop.getDesktop().browse(new URI("spotify:"));

                    System.out.println("Warte 4 Sekunden, damit Spotify starten und sich beim Server melden kann...");
                    Thread.sleep(4000);

                    // Erneuter Versuch, Geräte abzufragen
                    synchronized (spotifyApi) {
                        devices = spotifyApi.getUsersAvailableDevices().build().execute();
                    }
                    if (devices.length > 0) {
                        targetDeviceId = devices[0].getId();
                    } else {
                        System.err.println("Spotify-App konnte nicht schnell genug gestartet werden.");
                    }
                }
            }

            // 3. Wiedergabe anfordern
            String trackUri = track.id().startsWith("spotify:track:") ? track.id() : "spotify:track:" + track.id();
            com.google.gson.JsonArray uris = new com.google.gson.JsonArray();
            uris.add(trackUri);

            synchronized (spotifyApi) {
                var playRequest = spotifyApi.startResumeUsersPlayback().uris(uris);

                // Zwingt die API, genau dieses Gerät zu nutzen (weckt es auf)
                if (targetDeviceId != null) {
                    playRequest.device_id(targetDeviceId);
                }

                playRequest.build().execute();
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
                            Integer durationMs = null;
                            if (context.getItem() instanceof se.michaelthelin.spotify.model_objects.specification.Track t) {
                                durationMs = t.getDurationMs();
                            }

                            if (currentPlayingId != null && !currentPlayingId.equals(track.id())) {
                                isRunning = false;
                            } else if (context.getIs_playing() && durationMs != null && progressMs != null) {
                                if ((durationMs - progressMs) <= POLL_INTERVAL_MS) {
                                    System.out.println("Song nähert sich dem natürlichen Ende.");
                                    isRunning = false;
                                }
                            }
                        }
                        if (isRunning) Thread.sleep(POLL_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ie) { break; }
                    }
                }
            });
            playbackThread.start();
            playbackThread.join();
            System.out.println("Play-Methode für " + track.name() + " regulär beendet.");
        } catch (Exception e) {
            handleError("Fehler beim Starten der Wiedergabe", e);
        }
    }

    @Override
    public void pause() {
        try {
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
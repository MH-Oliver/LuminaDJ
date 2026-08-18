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

    // NEU: Hält fest, welcher Song eigentlich laufen soll
    private String expectedTrackId = null;

    public SpotifyAdapter(SpotifyAuthenticator authenticator) {
        SpotifyAdapter.spotifyApi = authenticator.getSpotifyApi();
    }

    @Override
    public void play(Track track) {
        try {
            this.expectedTrackId = track.id();
            String trackUri = track.id().startsWith("spotify:track:") ? track.id() : "spotify:track:" + track.id();

            String targetDeviceId = null;
            Device[] devices;

            synchronized (spotifyApi) {
                devices = spotifyApi.getUsersAvailableDevices().build().execute();
            }

            // 1. Suche nach einem bereits aktiven oder verfügbaren Gerät
            if (devices.length > 0) {
                for (Device d : devices) {
                    if (d.getIs_active()) {
                        targetDeviceId = d.getId();
                        break;
                    }
                }
                if (targetDeviceId == null) targetDeviceId = devices[0].getId();
            }

            // 2. Gerät wecken, falls Spotify geschlossen war
            if (targetDeviceId == null) {
                System.out.println("Spotify: Kein Gerät gefunden. Starte Spotify im Hintergrund...");

                // NEU: Eigener Try-Catch-Block nur für den OS-Aufruf, damit die Schleife danach auf jeden Fall läuft
                try {
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        // Robusterer Windows-Aufruf für Protokolle wie spotify:
                        Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", trackUri});
                    } else if (os.contains("mac")) {
                        Runtime.getRuntime().exec(new String[]{"open", trackUri});
                    } else if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(trackUri));
                    }
                } catch (Exception e) {
                    System.out.println("Automatischer OS-Start fehlgeschlagen: " + e.getMessage());
                }

                // Polling: Wir fragen bis zu 15 Sekunden lang
                for (int i = 0; i < 15; i++) {
                    Thread.sleep(1000);
                    synchronized (spotifyApi) {
                        devices = spotifyApi.getUsersAvailableDevices().build().execute();
                    }
                    if (devices.length > 0) {
                        targetDeviceId = devices[0].getId();
                        System.out.println("Spotify-Gerät online nach " + (i + 1) + " Sekunden!");

                        try {
                            com.google.gson.JsonArray deviceIds = new com.google.gson.JsonArray();
                            deviceIds.add(targetDeviceId);
                            synchronized (spotifyApi) {
                                spotifyApi.transferUsersPlayback(deviceIds).play(false).build().execute();
                            }
                            Thread.sleep(500);
                        } catch (Exception ignored) {}

                        break;
                    }
                }
            }

            // 3. Play-Befehl über die API senden
            if (targetDeviceId != null) {
                com.google.gson.JsonArray uris = new com.google.gson.JsonArray();
                uris.add(trackUri);
                synchronized (spotifyApi) {
                    spotifyApi.startResumeUsersPlayback().uris(uris).device_id(targetDeviceId).build().execute();
                }
            } else {
                System.err.println("Konnte Spotify nicht automatisch starten. Bitte öffne die App manuell!");
                // WICHTIG: Das wirft den Fehler, den der DjSessionController fängt, um die Endlosschleife zu verhindern!
                throw new IllegalArgumentException("Kein aktives Spotify-Gerät gefunden.");
            }

            System.out.println("Spotify spielt jetzt: " + track.name());
            isRunning = true;

            // ... (Hier bleibt dein aktueller playbackThread = new Thread(() -> { ... ); Block exakt so, wie er ist)

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
        } catch (IllegalArgumentException e) {
            // NEU: Diese Exception leiten wir gezielt nach draußen weiter zum Controller
            throw e;
        } catch (Exception e) {
            System.err.println("Fehler beim Starten der Wiedergabe: " + e.getMessage());
        }
    }

    @Override
    public boolean isPlaying() {
        try {
            synchronized (spotifyApi) {
                CurrentlyPlayingContext ctx = spotifyApi.getInformationAboutUsersCurrentPlayback().build().execute();
                if (ctx != null && ctx.getItem() != null) {
                    // NEU: Wenn Spotify noch den alten Song meldet, lügen wir das Frontend an (false),
                    // damit es im "Loading" Status bleibt, bis der neue Song WIRKLICH läuft.
                    if (expectedTrackId != null && !expectedTrackId.equals(ctx.getItem().getId())) {
                        return false;
                    }
                    return ctx.getIs_playing();
                }
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long getPlaybackPosition() {
        try {
            synchronized (spotifyApi) {
                CurrentlyPlayingContext ctx = spotifyApi.getInformationAboutUsersCurrentPlayback().build().execute();
                if (ctx != null && ctx.getItem() != null) {
                    // NEU: Wenn Spotify noch den alten Song meldet, setzen wir die Position knallhart auf 0
                    if (expectedTrackId != null && !expectedTrackId.equals(ctx.getItem().getId())) {
                        return 0;
                    }
                    return ctx.getProgress_ms() != null ? ctx.getProgress_ms() : 0;
                }
                return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void pause() {
        try {
            synchronized (spotifyApi) {
                spotifyApi.pauseUsersPlayback().build().execute();
            }
        } catch (Exception e) {}
    }

    @Override
    public void resume() {
        try {
            synchronized (spotifyApi) {
                spotifyApi.startResumeUsersPlayback().build().execute();
            }
        } catch (Exception e) {}
    }

    @Override
    public void seek(long positionMs) {
        try {
            synchronized (spotifyApi) {
                spotifyApi.seekToPositionInCurrentlyPlayingTrack((int) positionMs).build().execute();
            }
        } catch (Exception e) {}
    }

    @Override
    public void skip() {
        isRunning = false;
        if (playbackThread != null) {
            playbackThread.interrupt();
        }
    }

    @Override
    public void setVolume(int level) {
        try {
            int volume = Math.max(0, Math.min(100, level));
            spotifyApi.setVolumeForUsersPlayback(volume).build().execute();
        } catch (Exception e) {}
    }

    @Override
    public void stop() {
        try {
            isRunning = false;
            if (playbackThread != null) {
                playbackThread.interrupt();
            }
            synchronized (spotifyApi) {
                spotifyApi.pauseUsersPlayback().build().execute();
            }
        } catch (Exception e) {}
    }

    @Override
    public void addToQueue(Track track) {
        try {
            String trackUri = track.id().startsWith("spotify:track:") ? track.id() : "spotify:track:" + track.id();
            spotifyApi.addItemToUsersPlaybackQueue(trackUri).build().execute();
        } catch (Exception e) {}
    }

    @Override
    public void setTrackFavoriteStatus(Track track, boolean isFavorite) {
        try {
            String pureId = track.id().replace("spotify:track:", "");
            synchronized (spotifyApi) {
                if (isFavorite) {
                    spotifyApi.saveTracksForUser(pureId).build().execute();
                } else {
                    spotifyApi.removeUsersSavedTracks(pureId).build().execute();
                }
            }
        } catch (Exception e) {}
    }
}
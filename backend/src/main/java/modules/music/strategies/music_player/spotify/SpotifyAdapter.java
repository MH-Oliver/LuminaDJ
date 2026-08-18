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

    private volatile boolean isCurrentlyPlaying = false;
    private volatile long currentProgressMs = 0;
    private volatile long lastUpdateTimestamp = 0;
    private volatile long lastSeekTimestamp = 0;

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

            System.out.println("Spotify spielt jetzt: " + track);
            isCurrentlyPlaying = true;
            currentProgressMs = 0;
            lastUpdateTimestamp = System.currentTimeMillis();
            isRunning = true;

            playbackThread = new Thread(() -> {
                try { Thread.sleep(3000); } catch (InterruptedException e) { return; }

                boolean hasStartedPlayingCorrectSong = false;
                int syncAttempts = 0;

                while (isRunning) {
                    try {
                        CurrentlyPlayingContext context = null;
                        synchronized (spotifyApi) {
                            try {
                                context = spotifyApi.getInformationAboutUsersCurrentPlayback().build().execute();
                            } catch (Exception apiEx) {
                                System.err.println("Spotify API Warnung (Background Check): " + apiEx.getMessage());
                            }
                        }

                        if (context != null && context.getItem() != null) {
                            String currentPlayingId = context.getItem().getId();
                            Integer progressMs = context.getProgress_ms();
                            Integer durationMs = null;
                            if (context.getItem() instanceof se.michaelthelin.spotify.model_objects.specification.Track t) {
                                durationMs = t.getDurationMs();
                            }

                            if (currentPlayingId != null) {
                                if (currentPlayingId.equals(track.id())) {
                                    hasStartedPlayingCorrectSong = true;
                                    syncAttempts = 0;

                                    // Alten Wert merken, bevor wir ihn überschreiben
                                    long oldProgress = currentProgressMs;

                                    isCurrentlyPlaying = context.getIs_playing() != null ? context.getIs_playing() : false;
                                    currentProgressMs = progressMs != null ? progressMs : 0;
                                    lastUpdateTimestamp = System.currentTimeMillis();

                                    // NEU: Erkennen, wenn Spotify bei leerer Queue abbricht oder "Next" gedrückt wird (Reset auf 0)
                                    // Wir triggern den Skip nur, wenn wir nicht gerade selbst über die UI auf 0 gespult haben!
                                    if (currentProgressMs < 1000 && oldProgress > 3000 && (System.currentTimeMillis() - lastSeekTimestamp > 3000)) {
                                        System.out.println("Externer Skip (Spotify-Reset auf 0) erkannt! Lade nächsten Song...");
                                        isRunning = false; // Löst das Laden des neuen Songs im Controller aus!
                                    }

                                    if (isCurrentlyPlaying && durationMs != null && progressMs != null) {
                                        if ((durationMs - progressMs) <= POLL_INTERVAL_MS) {
                                            System.out.println("Song nähert sich dem natürlichen Ende.");
                                            isRunning = false;
                                        }
                                    }
                                } else if (hasStartedPlayingCorrectSong) {
                                    System.out.println("Externer Skip erkannt (Neue ID)! Lade nächsten Song...");
                                    isRunning = false;
                                } else {
                                    syncAttempts++;
                                }
                            }
                        } else if (hasStartedPlayingCorrectSong) {
                            // NEU: Wenn der Song lief, aber jetzt kein Context mehr kommt,
                            // wurde er wahrscheinlich manuell pausiert (oder Gerät ist im Standby)
                            isCurrentlyPlaying = false;
                            lastUpdateTimestamp = System.currentTimeMillis();
                        } else {
                            syncAttempts++;
                        }

                        if (!hasStartedPlayingCorrectSong && syncAttempts > 10) {
                            System.err.println("Timeout: Spotify hat den Song nicht synchronisiert.");
                            isRunning = false;
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
            throw e;
        } catch (Exception e) {
            System.err.println("Fehler beim Starten der Wiedergabe: " + e.getMessage());
        }
    }

    @Override
    public boolean isPlaying() {
        return isCurrentlyPlaying;
    }

    @Override
    public long getPlaybackPosition() {
        // Rechnet die Zeit zwischen den 3-Sekunden-API-Checks künstlich hoch, für eine butterweiche UI!
        if (isCurrentlyPlaying) {
            return currentProgressMs + (System.currentTimeMillis() - lastUpdateTimestamp);
        }
        return currentProgressMs;
    }

    @Override
    public void pause() {
        try {
            synchronized (spotifyApi) {
                spotifyApi.pauseUsersPlayback().build().execute();
            }
            isCurrentlyPlaying = false; // Sofortiges UI-Feedback
        } catch (Exception e) {}
    }

    @Override
    public void resume() {
        try {
            synchronized (spotifyApi) {
                spotifyApi.startResumeUsersPlayback().build().execute();
            }
            isCurrentlyPlaying = true; // Sofortiges UI-Feedback
            lastUpdateTimestamp = System.currentTimeMillis();
        } catch (Exception e) {}
    }

    @Override
    public void seek(long positionMs) {
        try {
            synchronized (spotifyApi) {
                spotifyApi.seekToPositionInCurrentlyPlayingTrack((int) positionMs).build().execute();
            }
            currentProgressMs = positionMs;
            lastUpdateTimestamp = System.currentTimeMillis();
            lastSeekTimestamp = System.currentTimeMillis(); // <-- HIER SETZEN
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
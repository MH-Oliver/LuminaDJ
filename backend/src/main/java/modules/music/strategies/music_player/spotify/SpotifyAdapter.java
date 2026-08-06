package modules.music.strategies.music_player.spotify;

import com.google.gson.JsonArray;
import modules.music.strategies.core.MusicPlayerAdapter;
import modules.music.structures.Track;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.requests.data.player.PauseUsersPlaybackRequest;
import se.michaelthelin.spotify.requests.data.player.SetVolumeForUsersPlaybackRequest;
import se.michaelthelin.spotify.requests.data.player.StartResumeUsersPlaybackRequest;

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

        this.spotifyApi = spotifyApi;
    }

    @Override
    public void play(Track track) {
        try {
            String trackUri = track.id().startsWith("spotify:track:")
                    ? track.id()
                    : "spotify:track:" + track.id();

            JsonArray uris = new JsonArray();
            uris.add(trackUri);

            StartResumeUsersPlaybackRequest playRequest = spotifyApi
                    .startResumeUsersPlayback()
                    .uris(uris)
                    .build();

            playRequest.execute();
            System.out.println("Spotify spielt jetzt: " + track.name() + " (" + track.author() + ")");

            // NEU: Thread-Start zur asynchronen Überwachung des Playback-Status
            isRunning = true;
            playbackThread = new Thread(() -> {
                System.out.println("Playback-Monitoring Thread gestartet für: " + track.name());

                try {
                    // Kurz warten, damit Spotify Zeit hat, den Status auf den neuen Song zu aktualisieren
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                while (isRunning) {
                    try {
                        CurrentlyPlayingContext context = spotifyApi
                                .getInformationAboutUsersCurrentPlayback()
                                .build()
                                .execute();

                        if (context != null && context.getIs_playing() && context.getItem() != null) {
                            String currentPlayingId = context.getItem().getId();
                            Integer progressMs = context.getProgress_ms();
                            Integer durationMs = null;

                            // Sicheres Auslesen der Track-Dauer (Pattern Matching ab Java 16+)
                            if (context.getItem() instanceof se.michaelthelin.spotify.model_objects.specification.Track t) {
                                durationMs = t.getDurationMs();
                            }

                            // Prüfen, ob der Song manuell gewechselt wurde
                            if (currentPlayingId != null && !currentPlayingId.equals(track.id())) {
                                System.out.println("Anderer Song läuft. Monitoring wird beendet.");
                                isRunning = false;
                            }
                            // Prüfen, ob der Song auf natürliche Weise beendet ist / sich dem Ende nährt
                            else if (durationMs != null && progressMs != null) {
                                if ((durationMs - progressMs) <= POLL_INTERVAL_MS) {
                                    System.out.println("Song nähert sich dem Ende.");
                                    isRunning = false;
                                }
                            }
                        } else {
                            System.out.println("Wiedergabe hat gestoppt.");
                            isRunning = false;
                        }

                        if (isRunning) {
                            Thread.sleep(POLL_INTERVAL_MS);
                        }

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("Verbindung zur Spotify API fehlgeschlagen: " + e.getMessage());
                        // Falls die API streikt, kurz abwarten und es erneut versuchen
                        try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                }
            });

            playbackThread.start();

            // Blockieren des Main-Threads, damit der DjSessionController auf das Ende des Songs wartet
            playbackThread.join();
            System.out.println("Play-Methode für " + track.name() + " ist offiziell beendet.");

        } catch (Exception e) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            throw new IllegalArgumentException("Fehler beim Starten der Wiedergabe", e);
        }
    }

    @Override
    public void pause() {
        try {
            // NEU: Thread stoppen, ähnlich wie in der SmartphoneKameraStrategy
            isRunning = false;
            if (playbackThread != null) {
                playbackThread.interrupt();
            }

            PauseUsersPlaybackRequest pauseRequest = spotifyApi
                    .pauseUsersPlayback()
                    .build();
            pauseRequest.execute();
            System.out.println("Wiedergabe pausiert.");
        } catch (Exception e) {
            handleError("Fehler beim Pausieren", e);
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
            CurrentlyPlayingContext context = spotifyApi
                    .getInformationAboutUsersCurrentPlayback()
                    .build()
                    .execute();

            return (context != null) ? context.getProgress_ms() : 0;
        } catch (Exception e) {
            return 0;
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
}
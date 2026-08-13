// active-session/active-session.component.ts
import { Component, OnInit, OnDestroy } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ContextApiService } from '../services/context-api.service';
import { Subscription, interval } from 'rxjs';

@Component({
  selector: 'app-active-session',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './active-session.component.html',
  styleUrls: ['./active-session.component.scss']
})
export class ActiveSessionComponent implements OnInit, OnDestroy {
  spotifyUser = 'DJ_Lumina_Test';
  isCameraExpanded = true;
  isPlaying = false;

  currentSong = {
    title: 'Loading...',
    artist: 'Loading...',
    coverUrl: 'https://via.placeholder.com/150/1e1e1e/ffffff?text=Album+Cover'
  };

  durationMs = 0;
  progressMs = 0;
  progressPercent = 0;
  formattedProgress = '0:00';
  formattedDuration = '0:00';
  isFavorite = false;

  sessionTimeLeft = 'Berechne...';
  private totalSessionDurationMs = 0;
  private sessionStartTime?: Date;
  private lastStartTimeRaw: string | null = null;

  detectedGestures = [
    { name: 'thumbs up detected', timeAgo: '2 sec ago' },
    { name: 'swipe gesture detected (1/3 for skip)', timeAgo: '15 sec ago' },
    { name: 'thumbs down detected', timeAgo: '45 sec ago' }
  ];

  private countdownSub?: Subscription;
  private localProgressTimer: any;

  // Status-Variablen für das Smart Polling
  private isFetchingInit = false;
  private isWaitingForPlayback = true; // Wartet darauf, dass das Backend "isPlaying = true" meldet

  constructor(
    private readonly apiService: ContextApiService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.fetchUpdate();

    this.countdownSub = interval(1000).subscribe(() => {
      this.updateSessionTimer();
    });

    this.localProgressTimer = setInterval(() => {
      // Zählt nur hoch, wenn der Song WIRKLICH spielt und wir nicht gerade auf einen neuen Song warten
      if (this.isPlaying && this.durationMs > 0 && !this.isWaitingForPlayback) {
        this.progressMs += 1000;

        // Wenn der Song zu Ende ist, fordern wir das Frontend auf, nach dem nächsten Song zu suchen
        if (this.progressMs >= this.durationMs) {
          this.isWaitingForPlayback = true;
          this.fetchUpdate();
        }

        this.updateProgressUI();
      }
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.countdownSub) this.countdownSub.unsubscribe();
    if (this.localProgressTimer) clearInterval(this.localProgressTimer);
  }

  private updateProgressUI(): void {
    const safeProgress = Math.min(this.progressMs, this.durationMs);
    this.progressPercent = this.durationMs > 0 ? (safeProgress / this.durationMs) * 100 : 0;
    this.formattedProgress = this.formatTime(safeProgress);
  }

  private updateSessionTimer(): void {
    if (!this.sessionStartTime || !this.totalSessionDurationMs) {
      this.sessionTimeLeft = 'Lade...';
      return;
    }

    const now = new Date().getTime();
    const elapsedMs = now - this.sessionStartTime.getTime();
    const remainingMs = this.totalSessionDurationMs - elapsedMs;

    if (remainingMs <= 0) {
      this.sessionTimeLeft = '00:00 min';
      return;
    }

    const totalSeconds = Math.floor(remainingMs / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;

    const paddedMin = minutes.toString();
    const paddedSec = seconds.toString().padStart(2, '0');

    this.sessionTimeLeft = `${paddedMin}:${paddedSec} min`;
  }

  toggleCamera(): void {
    this.isCameraExpanded = !this.isCameraExpanded;
  }

  fetchUpdate(): void {
    this.apiService.updateSession().subscribe({
      next: (data) => {
        if (data && data.currentSong) {
          this.currentSong = {
            title: data.currentSong['Name'],
            artist: data.currentSong['Author'],
            coverUrl: data.currentSong['Album-Bild'] || this.currentSong.coverUrl
          };
          this.isPlaying = data.currentSong['isPlaying'];
          this.durationMs = data.currentSong['Song-Länge'] || 0;
          this.progressMs = data.currentSong['Abspiel-position'] || 0;

          this.updateProgressUI();
          this.formattedDuration = this.formatTime(this.durationMs);

          if (data.startTime && data.totalMinutes) {
            const rawStart = data.startTime.toString();
            if (this.lastStartTimeRaw !== rawStart) {
              this.lastStartTimeRaw = rawStart;
              this.totalSessionDurationMs = data.totalMinutes * 60 * 1000;

              const timeParts = typeof data.startTime === 'string' ? data.startTime.split(':') : data.startTime;
              const now = new Date();
              this.sessionStartTime = new Date(
                now.getFullYear(), now.getMonth(), now.getDate(),
                parseInt(timeParts[0] || '0', 10),
                parseInt(timeParts[1] || '0', 10),
                parseInt(timeParts[2] || '0', 10)
              );
            }
          }

          // SMART POLLING LOGIK
          // Wenn wir auf den Start warten (z.B. weil Spotify gerade öffnet oder der nächste Song lädt)
          if (this.isWaitingForPlayback || this.currentSong.title === 'Wird gestartet...') {

            // Haben wir einen echten Song der jetzt auch WIRKLICH spielt?
            if (this.isPlaying && this.durationMs > 0 && this.currentSong.title !== 'Wird gestartet...') {
              this.isWaitingForPlayback = false; // Polling stoppen, lokaler Timer übernimmt ab jetzt!
            } else if (!this.isFetchingInit) {
              // Wenn nicht, frage in 2 Sekunden nochmal nach
              this.isFetchingInit = true;
              setTimeout(() => {
                this.isFetchingInit = false;
                this.fetchUpdate();
              }, 2000);
            }
          }
        }
      },
      error: (err) => console.error('Error fetching session update', err)
    });
  }

  private formatTime(ms: number): string {
    if (!ms) return '0:00';
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  skipSong(): void {
    this.isWaitingForPlayback = true; // Setzt den Player in den Lade-Modus
    this.progressMs = 0;
    this.updateProgressUI();

    this.apiService.skipSong().subscribe({
      next: (data) => {
        console.log('Skipped. Next song is:', data.nextSong);
        this.fetchUpdate();
      },
      error: (err) => console.error('Error skipping song', err)
    });
  }

  toggleFavorite(): void {
    this.isFavorite = !this.isFavorite;
    this.apiService.toggleFavorite(this.isFavorite).subscribe({
      error: (err) => console.error('Error updating favorite state', err)
    });
  }

  togglePlayPause(): void {
    this.isPlaying = !this.isPlaying;
    this.apiService.togglePlayPause().subscribe({
      next: () => setTimeout(() => this.fetchUpdate(), 500),
      error: (err) => console.error('Error toggling play/pause', err)
    });
  }

  previousSong(): void {
    this.progressMs = 0;
    this.updateProgressUI();
    this.apiService.previousSong().subscribe({
      next: () => setTimeout(() => this.fetchUpdate(), 500)
    });
  }

  onProgressBarClick(event: MouseEvent): void {
    const bar = event.currentTarget as HTMLElement;
    const rect = bar.getBoundingClientRect();
    const clickX = event.clientX - rect.left;
    const percent = Math.max(0, Math.min(1, clickX / rect.width));
    const seekMs = Math.floor(percent * this.durationMs);

    this.progressMs = seekMs;
    this.updateProgressUI();

    this.apiService.seek(seekMs).subscribe({
      next: () => setTimeout(() => this.fetchUpdate(), 500),
      error: (err) => console.error('Error seeking', err)
    });
  }

  editSessionAction(): void {
    this.apiService.editSession().subscribe({
      next: () => {
        this.router.navigate(['/session-setup'], { state: { preserveConfig: true } });
      },
      error: (err) => console.error('Error editing session', err)
    });
  }

  cancelSessionAction(): void {
    this.apiService.cancelSession().subscribe({
      next: () => {
        this.router.navigate(['/']);
      },
      error: (err) => console.error('Error canceling session', err)
    });
  }
}

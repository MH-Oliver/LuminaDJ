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
  isPlaying = true;

  currentSong = {
    title: 'Loading...',
    artist: 'Loading...',
    coverUrl: 'https://via.placeholder.com/150/1e1e1e/ffffff?text=Album+Cover'
  };

  // Player Variablen
  durationMs = 0;
  progressMs = 0;
  progressPercent = 0;
  formattedProgress = '0:00';
  formattedDuration = '0:00';
  isFavorite = false;

  // NEU: Session Timer Variablen
  sessionTimeLeft = 'Berechne...';
  private totalSessionDurationMs = 0;
  private sessionStartTime?: Date;

  detectedGestures = [
    { name: 'thumbs up detected', timeAgo: '2 sec ago' },
    { name: 'swipe gesture detected (1/3 for skip)', timeAgo: '15 sec ago' },
    { name: 'thumbs down detected', timeAgo: '45 sec ago' }
  ];

  private pollingSub?: Subscription;
  private countdownSub?: Subscription; // NEU: Separater Timer für den flüssigen Countdown

  constructor(
    private readonly apiService: ContextApiService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    // 1. Initialer Call (holt direkt Song UND Timer-Daten)
    this.fetchUpdate();

    // 2. Polling für Song-Updates
    this.pollingSub = interval(2000).subscribe(() => {
      this.fetchUpdate();
    });

    // 3. Jede Sekunde den UI-Countdown flüssig aktualisieren
    this.countdownSub = interval(1000).subscribe(() => {
      this.updateSessionTimer();
    });
  }

  ngOnDestroy(): void {
    if (this.pollingSub) this.pollingSub.unsubscribe();
    if (this.countdownSub) this.countdownSub.unsubscribe(); // Timer aufräumen
  }

  // ==========================================
  // NEU: TIMER LOGIK
  // ==========================================

  private initSessionTimer(contextData: any): void {
    try {
      // 1. Gesamtzeit ausrechnen (Summe aller Blöcke in Minuten * 60 * 1000)
      const phases = contextData.timeline?.phases || [];
      const totalMinutes = phases.reduce((sum: number, phase: any) => sum + (phase.durationMinutes || 0), 0);
      this.totalSessionDurationMs = totalMinutes * 60 * 1000;

      // 2. Start-Uhrzeit ROBUST aus dem Backend parsen
      let hours = 0, minutes = 0, seconds = 0;

      if (typeof contextData.startTime === 'string') {
        // Falls das Backend "19:30:00" sendet
        const timeParts = contextData.startTime.split(':');
        hours = parseInt(timeParts[0] || '0', 10);
        minutes = parseInt(timeParts[1] || '0', 10);
        seconds = parseInt(timeParts[2] || '0', 10);
      } else if (Array.isArray(contextData.startTime)) {
        // Falls das Backend ein Array [19, 30, 0] sendet (Spring Boot Standard)
        hours = contextData.startTime[0] || 0;
        minutes = contextData.startTime[1] || 0;
        seconds = contextData.startTime[2] || 0;
      }

      const now = new Date();
      this.sessionStartTime = new Date(
        now.getFullYear(),
        now.getMonth(),
        now.getDate(),
        hours,
        minutes,
        seconds
      );

      // Einmalig direkt ausführen
      this.updateSessionTimer();

    } catch (err) {
      console.error('Fehler beim Initialisieren des Timers:', err);
      this.sessionTimeLeft = 'Fehler';
    }
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
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    const paddedMin = minutes.toString().padStart(2, '0');
    const paddedSec = seconds.toString().padStart(2, '0');

    if (hours > 0) {
      this.sessionTimeLeft = `${hours}:${paddedMin}:${paddedSec} h`;
    } else {
      this.sessionTimeLeft = `${paddedMin}:${paddedSec} min`;
    }
  }

  // ==========================================
  // BESTEHENDE LOGIK
  // ==========================================

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
          this.progressPercent = this.durationMs > 0 ? (this.progressMs / this.durationMs) * 100 : 0;

          this.formattedProgress = this.formatTime(this.progressMs);
          this.formattedDuration = this.formatTime(this.durationMs);

          // NEU: Timer initialisieren, falls er noch nicht läuft
          if (data.startTime && data.totalMinutes && !this.sessionStartTime) {
            this.totalSessionDurationMs = data.totalMinutes * 60 * 1000;
            const timeParts = data.startTime.split(':');
            const now = new Date();
            this.sessionStartTime = new Date(
              now.getFullYear(), now.getMonth(), now.getDate(),
              parseInt(timeParts[0] || '0', 10),
              parseInt(timeParts[1] || '0', 10),
              parseInt(timeParts[2] || '0', 10)
            );
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
      next: (res) => console.log('Backend success:', res),
      error: (err) => console.error('Error updating favorite state', err)
    });
  }

  togglePlayPause(): void {
    this.isPlaying = !this.isPlaying;
    this.apiService.togglePlayPause().subscribe({
      next: () => this.fetchUpdate(),
      error: (err) => console.error('Error toggling play/pause', err)
    });
  }

  previousSong(): void {
    this.apiService.previousSong().subscribe({
      next: () => {
        this.progressMs = 0;
        this.progressPercent = 0;
        this.fetchUpdate();
      }
    });
  }

  onProgressBarClick(event: MouseEvent): void {
    const bar = event.currentTarget as HTMLElement;
    const rect = bar.getBoundingClientRect();
    const clickX = event.clientX - rect.left;
    const percent = Math.max(0, Math.min(1, clickX / rect.width));
    const seekMs = Math.floor(percent * this.durationMs);

    this.progressMs = seekMs;
    this.progressPercent = percent * 100;
    this.formattedProgress = this.formatTime(seekMs);

    this.apiService.seek(seekMs).subscribe({
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

import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ContextApiService } from '../services/context-api.service';
import { Subscription, interval } from 'rxjs';

import { FormsModule } from '@angular/forms';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { ButtonComponent } from '../shared/button/button.component';
import { NotificationService } from '../services/notification.service';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';

@Component({
  selector: 'app-active-session',
  standalone: true,
  imports: [CommonModule, FormsModule, MatSlideToggleModule, ButtonComponent, MatSelectModule, MatFormFieldModule],
  templateUrl: './active-session.component.html',
  styleUrls: ['./active-session.component.scss']
})
export class ActiveSessionComponent implements OnInit, OnDestroy {
  spotifyUser = 'DJ_Lumina_Test';
  isCameraExpanded = true;
  isPlaying = false;
  isCameraProcessing = true;
  isCameraReachable = false;
  isCameraSkipped = false;

  cameraImage: string | null = null;
  private cameraPollTimer: any;

  currentSong = {
    title: 'Loading...',
    artist: 'Loading...',
    coverUrl: 'https://via.placeholder.com/150/1e1e1e/ffffff?text=Album+Cover'
  };

  availableGestures = ['offene_hand', 'faust', 'peace', 'daumen_hoch', 'zeigefinger'];
  gestureMapping: { [key: string]: string } = {
    playPause: 'zeigefinger',
    skipGenre: 'peace',
    prioritize: 'daumen_hoch'
  };
  gestureActions = [
    { id: 'playPause', label: 'Play / Pause' },
    { id: 'skipGenre', label: 'Skip Genre' },
    { id: 'prioritize', label: 'Prioritize current genre' }
  ];
  detectedGestures: { name: string, count: number }[] = [];
  previousGestures: { [key: string]: number } = {};

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

  // TIMELINE DRAGGING LOGIK
  phases: any[] = [];
  totalMinutes = 120;
  elapsedMinutes = 0;
  ticks: number[] = [];
  isDraggingMarker = false;

  private countdownSub?: Subscription;
  private localProgressTimer: any;

  private isFetchingInit = false;
  private isWaitingForPlayback = true;

  constructor(
    private readonly apiService: ContextApiService,
    private readonly router: Router,
    private readonly notificationService: NotificationService // NEU injiziert
  ) {}

  ngOnInit(): void {
    this.isCameraSkipped = sessionStorage.getItem('skipCamera') === 'true';
    if (this.isCameraSkipped) {
      this.isCameraProcessing = false;
      this.isCameraExpanded = false;
    }

    this.fetchUpdate();

    this.countdownSub = interval(1000).subscribe(() => {
      this.updateSessionTimer();
    });

    this.localProgressTimer = setInterval(() => {
      if (this.isPlaying && this.durationMs > 0 && !this.isWaitingForPlayback) {
        this.progressMs += 1000;

        if (this.progressMs >= this.durationMs) {
          this.isWaitingForPlayback = true;
          this.fetchUpdate();
        }

        this.updateProgressUI();
      }
    }, 1000);

    this.cameraPollTimer = setInterval(() => {
      this.fetchCameraData();
    }, 100);
  }

  ngOnDestroy(): void {
    if (this.countdownSub) this.countdownSub.unsubscribe();
    if (this.localProgressTimer) clearInterval(this.localProgressTimer);
    if (this.cameraPollTimer) clearInterval(this.cameraPollTimer);
  }

  @HostListener('window:mousemove', ['$event'])
  onWindowMouseMove(event: MouseEvent): void {
    if (this.isDraggingMarker) {
      this.updateMarkerPosition(event);
    }
  }

  @HostListener('window:mouseup', ['$event'])
  onWindowMouseUp(event: MouseEvent): void {
    if (this.isDraggingMarker) {
      this.isDraggingMarker = false;
      this.isWaitingForPlayback = true;

      this.apiService.jumpSession(Math.round(this.elapsedMinutes)).subscribe({
        next: () => setTimeout(() => this.fetchUpdate(), 500),
        error: (err) => console.error('Error jumping session', err)
      });
    }
  }

  onMarkerMouseDown(event: MouseEvent): void {
    this.isDraggingMarker = true;
    this.updateMarkerPosition(event);
  }

  private updateMarkerPosition(event: MouseEvent): void {
    const bar = document.querySelector('.session-timeline-track') as HTMLElement;
    if (!bar) return;
    const rect = bar.getBoundingClientRect();
    const clickX = event.clientX - rect.left;
    let percent = clickX / rect.width;
    if (percent < 0) percent = 0;
    if (percent > 1) percent = 1;
    this.elapsedMinutes = percent * this.totalMinutes;
    this.updateSessionTimer();
  }

  private generateTicks(): void {
    this.ticks = [];
    for (let i = 0; i <= this.totalMinutes; i += 5) {
      this.ticks.push(i);
    }
  }

  onCameraToggleChange(): void {
    this.apiService.toggleCameraProcessing(this.isCameraProcessing).subscribe({
      error: (err) => console.error('Kamera-Toggle fehlgeschlagen', err)
    });

    if (!this.isCameraProcessing) {
      this.cameraImage = null;
    }
  }

  fetchCameraData(): void {
    if (this.isCameraExpanded && this.isCameraProcessing) {
      this.apiService.getCurrentFrame().subscribe({
        next: (data: any) => {
          this.isCameraReachable = true;
          this.cameraImage = data.image;

          if (data.gestures) {
            const currentGestures = data.gestures;

            // 1. DYNAMISCH: PLAY / PAUSE
            const playPauseGesture = this.gestureMapping['playPause'];
            const playPauseCount = currentGestures[playPauseGesture] || 0;
            const prevPlayPauseCount = this.previousGestures[playPauseGesture] || 0;
            if (playPauseCount > prevPlayPauseCount) {
              this.notificationService.showSuccess(`Geste '${playPauseGesture}' erkannt: Play/Pause`);
              this.togglePlayPause();
            }

            // 2. DYNAMISCH: PEACE -> GENRE SKIPPEN
            const skipGenreGesture = this.gestureMapping['skipGenre'];
            const skipGenreCount = currentGestures[skipGenreGesture] || 0;
            const prevSkipGenreCount = this.previousGestures[skipGenreGesture] || 0;
            if (skipGenreCount > prevSkipGenreCount) {
              this.notificationService.showSuccess(`Geste '${skipGenreGesture}' erkannt: Überspringe Genre...`);
              this.skipGenre();
            }

            // 3. DYNAMISCH: PRIORITIZE
            const prioritizeGesture = this.gestureMapping['prioritize'];
            const prioritizeCount = currentGestures[prioritizeGesture] || 0;
            const prevPrioritizeCount = this.previousGestures[prioritizeGesture] || 0;
            if (prioritizeCount > prevPrioritizeCount) {
              this.notificationService.showSuccess(`Geste '${prioritizeGesture}' erkannt: Genre & Vibe priorisiert!`);
              this.prioritizeCurrent();
            }

            this.previousGestures = { ...currentGestures };

            this.detectedGestures = Object.keys(currentGestures).map(key => ({
              name: key,
              count: currentGestures[key]
            }));
          }
        },
        error: (err) => {
          this.cameraImage = null;
          this.isCameraReachable = false;
        }
      });
    }
  }

  prioritizeCurrent(): void {
    this.apiService.prioritizeCurrentTrack().subscribe({
      error: (err) => console.error('Error prioritizing track', err)
    });
  }

  skipGenre(): void {
    let accumulatedTime = 0;
    let targetTime = this.totalMinutes;

    // Wir summieren die Längen der Blöcke auf, bis wir den Block finden,
    // der in der Zukunft liegt. Genau dort beginnt das neue Genre!
    for (const phase of this.phases) {
      accumulatedTime += phase.durationMinutes;
      // + 0.1 als winziger Puffer, falls wir genau auf der Grenze stehen
      if (accumulatedTime > this.elapsedMinutes + 0.1) {
        targetTime = accumulatedTime;
        break;
      }
    }

    if (targetTime >= this.totalMinutes) {
      this.notificationService.showError('Ende der Timeline erreicht.');
      return;
    }

    this.isWaitingForPlayback = true;

    // Wir runden den Wert, da das Backend einen Integer erwartet
    this.apiService.jumpSession(Math.round(targetTime)).subscribe({
      next: () => {
        this.elapsedMinutes = targetTime;
        this.updateSessionTimer();
        setTimeout(() => this.fetchUpdate(), 500);
      },
      error: (err) => console.error('Error skipping genre', err)
    });
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

    if (!this.isDraggingMarker) {
      const now = new Date().getTime();
      this.elapsedMinutes = (now - this.sessionStartTime.getTime()) / 60000;
      if (this.elapsedMinutes < 0) this.elapsedMinutes = 0;
    }

    const remainingMs = this.totalSessionDurationMs - (this.elapsedMinutes * 60000);

    if (remainingMs <= 0) {
      this.sessionTimeLeft = '00:00';
      return;
    }

    const totalSeconds = Math.floor(remainingMs / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;

    const paddedMin = minutes.toString();
    const paddedSec = seconds.toString().padStart(2, '0');

    this.sessionTimeLeft = `${paddedMin}:${paddedSec}`;
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

          if (data.timeline) {
            this.phases = data.timeline;
          }

          if (data.startTime && data.totalMinutes) {
            const rawStart = data.startTime.toString();
            if (this.lastStartTimeRaw !== rawStart) {
              this.lastStartTimeRaw = rawStart;
              this.totalMinutes = data.totalMinutes;
              this.totalSessionDurationMs = data.totalMinutes * 60 * 1000;
              this.generateTicks();

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

          const hasNoCover = this.currentSong.coverUrl.includes('Kein+Cover');
          const isDummy = this.currentSong.title === 'Wird gestartet...';

          // Status aktualisieren, falls wir vorher noch gewartet haben
          if (this.isPlaying && this.durationMs > 0 && !isDummy && !hasNoCover) {
            this.isWaitingForPlayback = false;
          }

        } // Ende von: if (data && data.currentSong)

        // NEU: IMMER weiter pollen! Unabhängig davon, ob der Song schon läuft oder nicht.
        // Das ist wichtig, um externe Klicks aus Spotify mitzubekommen.
        if (!this.isFetchingInit) {
          this.isFetchingInit = true;
          setTimeout(() => {
            this.isFetchingInit = false;
            this.fetchUpdate();
          }, 3000); // 3 Sekunden reichen völlig und schonen die API
        }

      },
      error: (err) => {
        console.error('Error fetching session update', err);
        // Auch bei einem Netzwerkfehler die Schleife am Leben halten!
        setTimeout(() => this.fetchUpdate(), 5000);
      }
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
    this.isWaitingForPlayback = true;
    this.progressMs = 0;
    this.updateProgressUI();

    this.apiService.skipSong().subscribe({
      next: () => this.fetchUpdate(),
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

import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ContextApiService } from '../services/context-api.service';
import { Subscription, interval } from 'rxjs';

import { FormsModule } from '@angular/forms';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { ButtonComponent } from '../shared/button/button.component';

@Component({
  selector: 'app-active-session',
  standalone: true,
  imports: [CommonModule, FormsModule, MatSlideToggleModule, ButtonComponent],
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
  detectedGestures: { name: string, count: number }[] = [];
  private cameraPollTimer: any;

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
    private readonly router: Router
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
            this.detectedGestures = Object.keys(data.gestures).map(key => ({
              name: key,
              count: data.gestures[key]
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

          if (this.isWaitingForPlayback || isDummy || hasNoCover) {
            if (this.isPlaying && this.durationMs > 0 && !isDummy && !hasNoCover) {
              this.isWaitingForPlayback = false;
            } else if (!this.isFetchingInit) {
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

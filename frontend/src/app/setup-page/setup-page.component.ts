import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ContextApiService } from '../services/context-api.service';
import { NotificationService } from '../services/notification.service';
import { ButtonComponent } from '../shared/button/button.component';
import { MatCheckboxModule } from '@angular/material/checkbox';

declare global {
  interface Window {
    electronAPI?: {
      openExternalUrl: (url: string) => Promise<void>;
    };
  }
}

@Component({
  selector: 'app-setup-page',
  standalone: true,
  imports: [FormsModule, CommonModule, MatFormFieldModule, MatInputModule, ButtonComponent, MatCheckboxModule],
  templateUrl: './setup-page.component.html',
  styleUrls: ['./setup-page.component.scss']
})
export class SetupPageComponent implements OnInit, OnDestroy {
  isSpotifyConnected = false;
  isCameraConnected = false;
  isCameraSkipped = false;
  showCameraDialog = false;
  isDetecting = false;
  detectedIp = '';
  manualIp = '';

  private spotifyPollTimer: any;
  private spotifyInitRetryTimer: any;

  constructor(
    private readonly apiService: ContextApiService,
    private readonly notificationService: NotificationService,
    private readonly router: Router
  ) {}

  ngOnInit() {
    this.checkSpotifyConnectionWithRetry();
  }

  ngOnDestroy() {
    if (this.spotifyPollTimer) clearInterval(this.spotifyPollTimer);
    if (this.spotifyInitRetryTimer) clearTimeout(this.spotifyInitRetryTimer);
  }

  private checkSpotifyConnectionWithRetry(remainingAttempts: number = 10): void {
    this.apiService.checkSpotifyConnection().subscribe({
      next: (res) => {
        if (res.connected) {
          this.isSpotifyConnected = true;
        }
      },
      error: (err) => {
        if (remainingAttempts > 1) {
          this.spotifyInitRetryTimer = setTimeout(() => {
            this.checkSpotifyConnectionWithRetry(remainingAttempts - 1);
          }, 1000);
          return;
        }
        console.error('Fehler beim Check der Spotify Verbindung:', err);
      }
    });
  }

  submitSetup(): void {
    if (this.isSpotifyConnected && (this.isCameraConnected || this.isCameraSkipped)) {

      if (this.isCameraSkipped) {
        sessionStorage.setItem('skipCamera', 'true');
      } else {
        sessionStorage.removeItem('skipCamera');
      }

      this.router.navigate(['/session-setup']);
    }
  }

  connectSpotify() {
    this.apiService.getSpotifyAuthUrl().subscribe({
      next: (res) => {
        if (res.status === 'already_connected') {
          this.isSpotifyConnected = true;
        } else if (res.url) {
          this.openInExternalBrowser(res.url);
          if (this.spotifyPollTimer) clearInterval(this.spotifyPollTimer);

          this.spotifyPollTimer = setInterval(() => {
            this.apiService.checkSpotifyConnection().subscribe({
              next: (checkRes) => {
                if (checkRes.connected) {
                  clearInterval(this.spotifyPollTimer);
                  this.isSpotifyConnected = true;
                }
              }
            });
          }, 2000);
        }
      },
      error: (err) => console.error('Fehler beim Abrufen der Spotify URL:', err)
    });
  }

  private openInExternalBrowser(url: string) {
    if (window.electronAPI?.openExternalUrl) {
      window.electronAPI.openExternalUrl(url).catch((err) => {
        console.warn('Externes Öffnen über Electron fehlgeschlagen, nutze Browser-Fallback.', err);
        window.open(url, '_blank', 'noopener,noreferrer');
      });
      return;
    }

    window.open(url, '_blank', 'noopener,noreferrer');
  }

  connectCamera() {
    this.showCameraDialog = true;
    this.detectedIp = '';
    this.manualIp = '';
  }

  closeCameraDialog() {
    this.showCameraDialog = false;
  }

  autoDetectCamera() {
    this.isDetecting = true;
    this.apiService.deviceFound().subscribe({
      next: (response: any) => {
        this.detectedIp = response.ip;
        this.isDetecting = false;
        this.manualIp = '';
        this.notificationService.showSuccess('Kamera erfolgreich gefunden!');
      },
      error: (err) => {
        console.error('Camera Auto Detect Error', err);
        this.isDetecting = false;
        const errMsg = err.error?.error || 'Fehler beim automatischen Suchen der Kamera.';
        this.notificationService.showError(errMsg);
      }
    });
  }

  confirmCameraIp() {
    const finalIp = this.manualIp ? this.manualIp.trim() : this.detectedIp;
    if (!finalIp) return;

    this.apiService.selectedDevice(finalIp).subscribe({
      next: () => {
        this.isCameraConnected = true;
        this.showCameraDialog = false;
      },
      error: (err) => {
        console.error('Device Selection Error', err);
        const errMsg = err.error?.error || 'Die Kamera konnte nicht verbunden werden.';
        this.notificationService.showError(errMsg);
      }
    });
  }
}

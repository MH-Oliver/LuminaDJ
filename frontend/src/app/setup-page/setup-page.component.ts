// setup-page/setup-page.component.ts
import { Component, OnInit, OnDestroy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ContextApiService } from '../services/context-api.service';

@Component({
  selector: 'app-setup-page',
  standalone: true,
  imports: [RouterLink, FormsModule],
  templateUrl: './setup-page.component.html',
  styleUrls: ['./setup-page.component.scss']
})
export class SetupPageComponent implements OnInit, OnDestroy {
  isSpotifyConnected = false;
  isCameraConnected = false;

  // Camera Dialog State
  showCameraDialog = false;
  isDetecting = false;
  detectedIp = '';
  manualIp = '';

  private spotifyPollTimer: any;

  constructor(private readonly apiService: ContextApiService) {}

  ngOnInit() {
    // Beim Laden nur prüfen, ob wir evtl. schon eingeloggt sind
    this.apiService.checkSpotifyConnection().subscribe({
      next: (res) => {
        if (res.connected) {
          this.isSpotifyConnected = true;
        }
      },
      error: (err) => console.error('Fehler beim Check der Spotify Verbindung:', err)
    });
  }

  ngOnDestroy() {
    if (this.spotifyPollTimer) clearInterval(this.spotifyPollTimer);
  }

  connectSpotify() {
    this.apiService.getSpotifyAuthUrl().subscribe({
      next: (res) => {
        if (res.status === 'already_connected') {
          this.isSpotifyConnected = true;
        } else if (res.url) {
          // 1. Öffnet den nativen Browser (Chrome, Firefox etc.) über Electron
          this.openInExternalBrowser(res.url);

          // 2. Polling starten, um zu checken, ob der Login im echten Browser erfolgreich war
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
    // Versuch 1: Wir prüfen, ob wir in Electron sind (mit Node-Integration)
    if (typeof window !== 'undefined' && (window as any).require) {
      try {
        const electron = (window as any).require('electron');
        if (electron && electron.shell) {
          electron.shell.openExternal(url);
          return;
        }
      } catch (e) {
        console.warn('Electron require fehlgeschlagen, nutze Fallback.', e);
      }
    }
    // Versuch 2: Standard-Browser Fallback (falls Node-Integration in Electron deaktiviert ist)
    window.open(url, '_blank');
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
      next: (response) => {
        this.detectedIp = response.ip;
        this.isDetecting = false;
        this.manualIp = '';
      },
      error: (err) => {
        console.error('Camera Auto Detect Error', err);
        this.isDetecting = false;
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
      error: (err) => console.error('Device Selection Error', err)
    });
  }
}

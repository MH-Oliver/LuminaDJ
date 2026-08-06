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
  sessionTimeLeft = '14:53 min';
  isCameraExpanded = true;

  currentSong = {
    title: 'Loading...',
    artist: 'Loading...',
    coverUrl: 'https://via.placeholder.com/150/1e1e1e/ffffff?text=Album+Cover'
  };

  detectedGestures = [
    { name: 'thumbs up detected', timeAgo: '2 sec ago' },
    { name: 'swipe gesture detected (1/3 for skip)', timeAgo: '15 sec ago' },
    { name: 'thumbs down detected', timeAgo: '45 sec ago' }
  ];

  private pollingSub?: Subscription;

  constructor(
    private readonly apiService: ContextApiService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.fetchUpdate(); // Initialer Call

    // Alle 2 Sekunden nach dem aktuellen Song fragen
    this.pollingSub = interval(2000).subscribe(() => {
      this.fetchUpdate();
    });
  }

  ngOnDestroy(): void {
    if (this.pollingSub) {
      this.pollingSub.unsubscribe();
    }
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
        }
      },
      error: (err) => console.error('Error fetching session update', err)
    });
  }

  skipSong(): void {
    this.apiService.skipSong().subscribe({
      next: (data) => {
        console.log('Skipped. Next song is:', data.nextSong);
        this.fetchUpdate(); // Aktualisiert die Anzeige direkt
      },
      error: (err) => console.error('Error skipping song', err)
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

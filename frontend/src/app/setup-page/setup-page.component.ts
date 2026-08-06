import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ContextApiService } from '../services/context-api.service';

@Component({
  selector: 'app-setup-page',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './setup-page.component.html',
  styleUrls: ['./setup-page.component.scss']
})
export class SetupPageComponent {
  isSpotifyConnected = false;
  isCameraConnected = false;

  constructor(private readonly apiService: ContextApiService) {}

  connectSpotify() {
    this.apiService.connectSpotify().subscribe({
      next: (response) => {
        if (response.success) {
          this.isSpotifyConnected = true;
        }
      },
      error: (err) => console.error('Spotify Connection Error', err)
    });
  }

  connectCamera() {
    this.apiService.deviceFound().subscribe({
      next: (response) => {
        this.apiService.selectedDevice(response.ip).subscribe({
          next: () => {
            this.isCameraConnected = true;
          },
          error: (err) => console.error('Device Selection Error', err)
        });
      },
      error: (err) => console.error('Camera Connection Error', err)
    });
  }
}

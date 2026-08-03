import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-setup-page',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './setup-page.component.html',
  styleUrls: ['./setup-page.component.css']
})
export class SetupPageComponent {
  isSpotifyConnected = false;
  isCameraConnected = false;

  connectSpotify() {
    this.isSpotifyConnected = true; // Setzt den Haken
  }

  connectCamera() {
    this.isCameraConnected = true; // Setzt den Haken
  }
}

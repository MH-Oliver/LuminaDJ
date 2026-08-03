import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-active-session',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './active-session.component.html',
  styleUrls: ['./active-session.component.css']
})
export class ActiveSessionComponent {
  spotifyUser = 'DJ_Lumina_Test';
  sessionTimeLeft = '14:53 min';

  currentSong = {
    title: 'Bohemian Rhapsody',
    artist: 'Queen',
    coverUrl: 'https://via.placeholder.com/150/1e1e1e/ffffff?text=Album+Cover'
  };

  detectedGestures = [
    { name: 'thumbs up detected', timeAgo: '2 sec ago' },
    { name: 'swipe gesture detected (1/3 for skip)', timeAgo: '15 sec ago' },
    { name: 'thumbs down detected', timeAgo: '45 sec ago' }
  ];
}

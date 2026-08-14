import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-start-page',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './start-page.component.html',
  styleUrls: ['./start-page.component.scss'] // Geändert zu .scss
})
export class StartPageComponent {}

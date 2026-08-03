import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router'; // <-- NEU HINZUGEFÜGT
import { ContextApiService } from './services/context-api.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet], // <-- NEU HINZUGEFÜGT: Erlaubt das Routing
  templateUrl: './app.html',
  styleUrl: './app.css',
})

export class App {
  statusMessage = 'Noch kein Request gesendet.';

  constructor(private readonly contextApiService: ContextApiService) {}

  sendDemoContext(): void {
    this.contextApiService.sendDummyContext().subscribe({
      next: () => {
        this.statusMessage = 'Dummy-Context erfolgreich an das Backend gesendet.';
      },
      error: () => {
        this.statusMessage = 'Backend nicht erreichbar oder Payload ungültig.';
      },
    });
  }
}

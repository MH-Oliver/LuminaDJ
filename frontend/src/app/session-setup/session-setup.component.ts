import { Component, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ContextApiService, UserContextDto, TimelinePhaseDto } from '../services/context-api.service';

interface GenreBlock {
  id: number;
  title: string;
  start: number;
  duration: number;
  row: number;
}

@Component({
  selector: 'app-session-setup',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './session-setup.component.html',
  styleUrls: ['./session-setup.component.scss']
})
export class SessionSetupComponent implements OnInit {
  spotifyUser = 'DJ_Lumina_Test';
  totalMinutes = 120;

  // Werden dynamisch aus dem Backend befüllt
  availableGenres: string[] = [];
  availablePresets: string[] = [];

  // Start-Blöcke (können auch als leeres Array [] initialisiert werden)
  blocks: GenreBlock[] = [];

  draggingBlock: GenreBlock | null = null;
  resizingBlock: GenreBlock | null = null;
  startX = 0;
  startValue = 0;
  wasDragged = false;
  selectedBlock: GenreBlock | null = null;
  isDraggingOutside = false;
  errorMessage: string | null = null;

  constructor(
    private readonly apiService: ContextApiService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    // 1. Alle verfügbaren Presets laden
    this.apiService.loadPresets().subscribe({
      next: (presets) => this.availablePresets = presets,
      error: (err) => console.error('Fehler beim Laden der Presets:', err)
    });

    // 2. Verfügbare Genres laden
    this.apiService.loadGenre('').subscribe({
      next: (genres) => {
        if (genres && genres.length > 0) {
          this.availableGenres = genres;
        }
      },
      error: (err) => console.error('Fehler beim Laden der Genres:', err)
    });
  }

  // ==========================================
  // JSON -> UI: Preset vom Backend laden
  // ==========================================
  onPresetChange(event: Event): void {
    const presetName = (event.target as HTMLSelectElement).value;
    if (!presetName) return;

    this.apiService.selectPreset(presetName).subscribe({
      next: (data) => {
        console.log("Empfangenes Preset vom Backend:", data); // Hilft bei der Fehlersuche in der Konsole

        // Flexibel: Akzeptiert { timeline: { phases: [...] } } ODER direkt { phases: [...] }
        const phases = data?.timeline?.phases || data?.phases;

        if (phases && Array.isArray(phases)) {
          this.convertJsonToBlocks(phases);
        } else {
          this.errorMessage = 'Das geladene Preset hat ein ungültiges Format.';
          console.error('Unerwartetes Datenformat:', data);
        }
      },
      error: (err) => {
        this.errorMessage = 'Fehler beim Laden des Presets.';
        console.error(err);
      }
    });
  }

  private convertJsonToBlocks(phases: any[]): void {
    this.blocks = [];
    let currentStart = 0;
    let currentRow = 0;

    phases.forEach((phase, index) => {
      // Robustes Auslesen: Falls dein Java-Backend "duration" statt "durationMinutes" sendet
      const phaseDuration = phase.durationMinutes ?? phase.duration ?? 30; // Fallback auf 30, falls nichts gefunden wird

      // Falls das Genre als Objekt { name: "TECHNO" } ankommt, ansonsten String
      const phaseGenre = typeof phase.genre === 'object' ? phase.genre.name : phase.genre;

      this.blocks.push({
        id: Date.now() + index,
        title: phaseGenre ? phaseGenre.toString() : 'UNKNOWN',
        start: currentStart,
        duration: Number(phaseDuration),
        row: currentRow
      });

      currentStart += Number(phaseDuration);
      currentRow = currentRow === 0 ? 1 : 0; // Wechselt abwechselnd zwischen Zeile 0 und 1
    });

    // Passe die Timeline-Gesamtlänge im UI an (auf die nächsten 5 Minuten gerundet)
    this.totalMinutes = Math.max(120, Math.ceil(currentStart / 5) * 5);
  }

  // ==========================================
  // UI -> JSON: Timeline an Backend senden
  // ==========================================
  onReady(): void {
    if (this.blocks.length === 0) {
      this.errorMessage = 'Bitte füge mindestens ein Genre zur Timeline hinzu.';
      return;
    }

    // 1. Sortiere die Blöcke streng nach Startzeit (chronologisch)
    const sortedBlocks = [...this.blocks].sort((a, b) => a.start - b.start);

    // 2. Wandle die grafischen Blöcke in Backend-Phasen (TimelinePhaseDto) um
    const phases: TimelinePhaseDto[] = sortedBlocks.map(block => {
      // Formatiert Titel sicher für das Java-Enum (z.B. "Hip Hop" -> "HIP_HOP")
      const safeGenre = block.title.toUpperCase().replace(/\s+/g, '_');

      return {
        genre: safeGenre,
        durationMinutes: block.duration,
        transitionOutMinutes: 5 // Vorerst statischer Default-Wert
      };
    });

    // 3. Baue das finale JSON (UserContextDto)
    const payload: UserContextDto = {
      tempo: 120,
      location: "Bar", // Backend-Enum Location
      startTime: new Date().toTimeString().split(' ')[0], // z.B. "19:30:00"
      timeline: { phases: phases },
      songCooldownMinutes: 30
    };

    // 4. Abschicken und Weiterleiten
    this.apiService.sendContext(payload).subscribe({
      next: () => {
        // Erfolgreich ans Backend gesendet -> Wechsel zum aktiven Session Player
        this.router.navigate(['/active-session']);
      },
      error: (err) => {
        this.errorMessage = 'Fehler beim Senden der Timeline an das Backend.';
        console.error(err);
      }
    });
  }

  // ==========================================
  // BESTEHENDE DRAG & DROP UI LOGIK
  // ==========================================

  get ticks(): number[] {
    const tickArray = [];
    for (let i = 0; i <= this.totalMinutes; i += 10) {
      tickArray.push(i);
    }
    return tickArray;
  }

  get gridBackgroundSize(): string {
    return `${(5 / this.totalMinutes) * 100}% 100%`;
  }

  getLeft(block: GenreBlock): string { return (block.start / this.totalMinutes) * 100 + '%'; }

  getWidth(block: GenreBlock): string { return (block.duration / this.totalMinutes) * 100 + '%'; }

  updateTimelineLength(event: Event): void {
    const val = +(event.target as HTMLInputElement).value;
    if (val && val >= 10 && val <= 600) {
      this.totalMinutes = Math.round(val / 5) * 5;

      this.blocks = this.blocks.filter(b => b.start < this.totalMinutes);
      this.blocks.forEach(b => {
        if (b.start + b.duration > this.totalMinutes) {
          b.duration = this.totalMinutes - b.start;
        }
      });
    } else {
      (event.target as HTMLInputElement).value = this.totalMinutes.toString();
    }
  }

  onMouseDownMove(event: MouseEvent, block: GenreBlock): void {
    event.stopPropagation();
    this.draggingBlock = block;
    this.startX = event.clientX;
    this.startValue = block.start;
    this.wasDragged = false;
    this.isDraggingOutside = false;
  }

  onMouseDownResize(event: MouseEvent, block: GenreBlock): void {
    event.stopPropagation();
    this.resizingBlock = block;
    this.startX = event.clientX;
    this.startValue = block.duration;
    this.wasDragged = false;
  }

  @HostListener('window:mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    if (!this.draggingBlock && !this.resizingBlock) return;
    this.wasDragged = true;

    const timelineEl = document.querySelector('.timeline-tracks') as HTMLElement;
    if (!timelineEl) return;

    const rect = timelineEl.getBoundingClientRect();
    const pixelsPerMinute = rect.width / this.totalMinutes;
    const deltaMinutes = (event.clientX - this.startX) / pixelsPerMinute;

    if (this.draggingBlock) {
      const isOutY = event.clientY < rect.top - 30 || event.clientY > rect.bottom + 30;
      const isOutX = event.clientX < rect.left - 30 || event.clientX > rect.right + 30;
      this.isDraggingOutside = isOutY || isOutX;

      if (!this.isDraggingOutside) {
        const midPoint = rect.top + (rect.height / 2);
        this.draggingBlock.row = event.clientY < midPoint ? 0 : 1;
      }

      let newStart = this.startValue + deltaMinutes;
      newStart = Math.round(newStart / 5) * 5;

      if (newStart < 0) newStart = 0;
      if (newStart + this.draggingBlock.duration > this.totalMinutes) {
        newStart = this.totalMinutes - this.draggingBlock.duration;
      }
      this.draggingBlock.start = newStart;
    }

    if (this.resizingBlock) {
      let newDuration = this.startValue + deltaMinutes;
      newDuration = Math.round(newDuration / 5) * 5;

      if (newDuration < 5) newDuration = 5;
      if (this.resizingBlock.start + newDuration > this.totalMinutes) {
        newDuration = this.totalMinutes - this.resizingBlock.start;
      }
      this.resizingBlock.duration = newDuration;
    }
  }

  @HostListener('window:mouseup')
  onMouseUp(): void {
    let activeBlock = this.draggingBlock || this.resizingBlock;

    if (this.draggingBlock && this.isDraggingOutside) {
      this.blocks = this.blocks.filter(b => b.id !== this.draggingBlock!.id);
      activeBlock = null;
    }

    if (activeBlock) {
      this.resolveOverlaps(activeBlock);
    }

    this.draggingBlock = null;
    this.resizingBlock = null;
    this.isDraggingOutside = false;
  }

  private resolveOverlaps(activeBlock: GenreBlock): void {
    const aStart = activeBlock.start;
    const aEnd = activeBlock.start + activeBlock.duration;

    for (let i = this.blocks.length - 1; i >= 0; i--) {
      const b = this.blocks[i];
      if (b.id === activeBlock.id || b.row !== activeBlock.row) continue;

      const bStart = b.start;
      const bEnd = b.start + b.duration;

      if (aStart < bEnd && aEnd > bStart) {
        if (aStart <= bStart && aEnd >= bEnd) {
          this.blocks.splice(i, 1);
        } else if (aStart <= bStart && aEnd < bEnd) {
          b.start = aEnd;
          b.duration = bEnd - aEnd;
        } else if (aStart > bStart && aEnd >= bEnd) {
          b.duration = aStart - bStart;
        } else if (aStart > bStart && aEnd < bEnd) {
          b.duration = aStart - bStart;
          this.blocks.push({
            id: Date.now() + Math.random(),
            title: b.title,
            start: aEnd,
            duration: bEnd - aEnd,
            row: b.row
          });
        }
      }
    }
  }

  openDialog(block: GenreBlock): void {
    if (this.wasDragged) {
      this.wasDragged = false;
      return;
    }
    this.selectedBlock = block;
  }

  closeDialog(): void {
    this.selectedBlock = null;
  }

  deleteSelectedBlock(): void {
    if (this.selectedBlock) {
      this.blocks = this.blocks.filter(b => b.id !== this.selectedBlock!.id);
      this.closeDialog();
    }
  }

  closeError(): void {
    this.errorMessage = null;
  }

  updateGenre(event: Event): void {
    const newGenre = (event.target as HTMLSelectElement).value;
    if (this.selectedBlock) {
      this.selectedBlock.title = newGenre;
    }
  }

  updateDuration(event: Event): void {
    const val = +(event.target as HTMLInputElement).value;
    if (this.selectedBlock) {
      this.selectedBlock.duration = val;
      this.resolveOverlaps(this.selectedBlock);
    }
  }

  addBlock(row: number): void {
    const maxEnd = this.blocks.reduce((max, b) => Math.max(max, b.start + b.duration), 0);

    if (maxEnd + 5 > this.totalMinutes) {
      this.errorMessage = 'Die Timeline ist voll! Es kann kein weiteres Genre mehr eingefügt werden.';
      return;
    }

    this.blocks.push({
      id: Date.now(),
      title: this.availableGenres.length > 0 ? this.availableGenres[0] : 'NEW',
      start: maxEnd,
      duration: 5,
      row: row
    });
  }
}

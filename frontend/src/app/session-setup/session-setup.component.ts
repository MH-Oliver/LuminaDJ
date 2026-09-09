import {Component, computed, HostListener, OnInit, signal} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ContextApiService, UserContextDto, TimelinePhaseDto } from '../services/context-api.service';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatSliderModule } from '@angular/material/slider';
import { ButtonComponent } from '../shared/button/button.component';
import { MatTooltipModule } from '@angular/material/tooltip';
import {MatAutocomplete, MatAutocompleteTrigger} from '@angular/material/autocomplete';

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
  imports: [CommonModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatSliderModule, ButtonComponent, MatTooltipModule, MatAutocomplete, MatAutocompleteTrigger],
  templateUrl: './session-setup.component.html',
  styleUrls: ['./session-setup.component.scss']
})
export class SessionSetupComponent implements OnInit {
  spotifyUser = 'DJ_Lumina_Test';
  totalMinutes = 120;

  availableGenres = signal<string[]>([]);
  searchQuery = signal<string>('');
  availablePresets: string[] = [];

  filteredGenres = computed(() => {
    const query = this.searchQuery().toLowerCase();
    return this.availableGenres().filter(g => g.toLowerCase().includes(query));
  });

  blocks: GenreBlock[] = [];
  draggingBlock: GenreBlock | null = null;
  resizingBlock: GenreBlock | null = null;

  elapsedMinutes = 0;

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
    this.apiService.loadPresets().subscribe({
      next: (presets) => this.availablePresets = presets,
      error: (err) => console.error('Fehler beim Laden der Presets:', err)
    });
    this.apiService.loadGenre('').subscribe({
      next: (genres) => {
        if (genres && genres.length > 0) {
          this.availableGenres.set(genres);
        }
      },
      error: (err) => console.error('Fehler beim Laden der Genres:', err)
    });

    if (history.state && history.state.preserveConfig) {
      this.apiService.getCurrentContext().subscribe({
        next: (data) => {
          const phases = data?.timeline?.phases || data?.phases;
          if (phases && Array.isArray(phases)) {
            this.convertJsonToBlocks(phases);
          }

          if (data?.startTime) {
            const timeParts = typeof data.startTime === 'string' ? data.startTime.split(':') : data.startTime;
            const now = new Date();
            const sessionStartTime = new Date(
              now.getFullYear(), now.getMonth(), now.getDate(),
              parseInt(timeParts[0] || '0', 10),
              parseInt(timeParts[1] || '0', 10),
              parseInt(timeParts[2] || '0', 10)
            );
            const elapsedMs = now.getTime() - sessionStartTime.getTime();
            this.elapsedMinutes = Math.max(0, elapsedMs / 60000);
            if (this.elapsedMinutes > this.totalMinutes) this.elapsedMinutes = this.totalMinutes;
          }
        },
        error: (err) => console.error('Keine vorherige Session gefunden:', err)
      });
    }
  }
  onPresetChange(event: any): void {
    const presetName = event.value || event.target?.value;
    if (!presetName) return;

    this.apiService.selectPreset(presetName).subscribe({
      next: (data) => {
        console.log("Empfangenes Preset vom Backend:", data);
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

    let currentBackendTime = 0;
    let nextUiStart = 0;
    let currentRow = 0;

    phases.forEach((phase, index) => {
      const phaseDuration = Number(phase.durationMinutes ?? phase.duration ?? 30);
      const transitionOut = Number(phase.transitionOutMinutes ?? 5);
      const phaseGenre = typeof phase.genre === 'object' ? phase.genre.name : phase.genre;
      const uiStart = nextUiStart;
      const uiEnd = currentBackendTime + phaseDuration;
      let uiDuration = uiEnd - uiStart;
      if (uiDuration < 5) uiDuration = 5;

      this.blocks.push({
        id: Date.now() + index,
        title: phaseGenre ? phaseGenre.toString() : 'UNKNOWN',
        start: uiStart,
        duration: uiDuration,
        row: currentRow
      });
      currentBackendTime += phaseDuration;
      nextUiStart = currentBackendTime - transitionOut;
      if (nextUiStart < 0) nextUiStart = 0;

      currentRow = currentRow === 0 ? 1 : 0;
    });
    this.totalMinutes = Math.max(120, Math.ceil(currentBackendTime / 5) * 5);
  }
  onReady(): void {
    if (this.blocks.length === 0) {
      this.errorMessage = 'Bitte füge mindestens ein Genre zur Timeline hinzu.';
      return;
    }
    const sortedBlocks = [...this.blocks].sort((a, b) => a.start - b.start);

    let currentBackendTime = 0;
    const phases: TimelinePhaseDto[] = sortedBlocks.map((block, i) => {
      const safeGenre = block.title.toUpperCase().replace(/\s+/g, '_');
      const nextBlock = sortedBlocks[i + 1];
      const blockEnd = block.start + block.duration;
      let phaseDuration = blockEnd - currentBackendTime;
      if (phaseDuration < 0) phaseDuration = 0;

      let transitionOut = 0;
      if (nextBlock) {
        transitionOut = blockEnd - nextBlock.start;
        if (transitionOut < 0) transitionOut = 0;
        if (transitionOut > phaseDuration) transitionOut = phaseDuration;
      }

      currentBackendTime = blockEnd;

      return {
        genre: safeGenre,
        durationMinutes: phaseDuration,
        transitionOutMinutes: transitionOut
      };
    });

    const adjustedStartTime = new Date(new Date().getTime() - (this.elapsedMinutes * 60000));
    const startTimeString = adjustedStartTime.toTimeString().split(' ')[0];

    const payload: UserContextDto = {
      tempo: 120,
      location: "Bar",
      startTime: startTimeString,
      timeline: { phases: phases },
      songCooldownMinutes: 100000,
      totalMinutes: this.totalMinutes
    };
    this.apiService.sendContext(payload).subscribe({
      next: () => {
        this.router.navigate(['/active-session']);
      },
      error: (err) => {
        this.errorMessage = 'Fehler beim Senden der Timeline an das Backend.';
        console.error(err);
      }
    });
  }
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

    const activeBlock = this.draggingBlock || this.resizingBlock;
    const snapPoints = this.getSnapPoints(activeBlock!.id);
    const SNAP_THRESHOLD = 1.5;

    if (this.draggingBlock) {
      const isOutY = event.clientY < rect.top - 30 || event.clientY > rect.bottom + 30;
      const isOutX = event.clientX < rect.left - 30 || event.clientX > rect.right + 30;
      this.isDraggingOutside = isOutY || isOutX;

      if (!this.isDraggingOutside) {
        const midPoint = rect.top + (rect.height / 2);
        this.draggingBlock.row = event.clientY < midPoint ? 0 : 1;
      }
      const rawStart = this.startValue + deltaMinutes;
      const rawEnd = rawStart + this.draggingBlock.duration;

      let snappedStart = Math.round(rawStart / 5) * 5;
      let minDiff = SNAP_THRESHOLD;
      for (const p of snapPoints) {
        const diffStart = Math.abs(rawStart - p);
        if (diffStart < minDiff) {
          minDiff = diffStart;
          snappedStart = p;
        }
        const diffEnd = Math.abs(rawEnd - p);
        if (diffEnd < minDiff) {
          minDiff = diffEnd;
          snappedStart = p - this.draggingBlock.duration;
        }
      }

      let newStart = snappedStart;
      if (newStart < 0) newStart = 0;
      if (newStart + this.draggingBlock.duration > this.totalMinutes) {
        newStart = this.totalMinutes - this.draggingBlock.duration;
      }
      this.draggingBlock.start = newStart;
    }

    if (this.resizingBlock) {
      const rawEnd = this.resizingBlock.start + this.startValue + deltaMinutes;

      let snappedEnd = Math.round(rawEnd / 5) * 5;
      let minDiff = SNAP_THRESHOLD;
      for (const p of snapPoints) {
        const diffEnd = Math.abs(rawEnd - p);
        if (diffEnd < minDiff) {
          minDiff = diffEnd;
          snappedEnd = p;
        }
      }

      let newDuration = snappedEnd - this.resizingBlock.start;
      if (newDuration < 1) newDuration = 1;

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
    this.searchQuery.set('');
  }

  updateGenreAuto(event: any): void {
    if (this.selectedBlock) {
      this.selectedBlock.title = event.option.value;
    }
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

  updateGenre(event: any): void {
    const newGenre = event.value || event.target?.value;
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

    const currentGenres = this.availableGenres();

    this.blocks.push({
      id: Date.now(),
      title: currentGenres.length > 0 ? currentGenres[0] : 'NEW',
      start: maxEnd,
      duration: 5,
      row: row
    });
  }

  private getSnapPoints(excludeId: number): number[] {
    const points = new Set<number>();
    points.add(0);
    points.add(this.totalMinutes);

    for (const b of this.blocks) {
      if (b.id !== excludeId) {
        points.add(b.start);
        points.add(b.start + b.duration);
      }
    }
    return Array.from(points);
  }
}

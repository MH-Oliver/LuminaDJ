import { Component, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

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
  imports: [RouterLink, CommonModule],
  templateUrl: './session-setup.component.html',
  styleUrls: ['./session-setup.component.css']
})
export class SessionSetupComponent {
  spotifyUser = 'DJ_Lumina_Test';
  totalMinutes = 120;

  availableGenres: string[] = [
    'EDM', 'Techno', 'House', 'Hip Hop', 'Pop', 'Rock', 'Acoustic', 'Afrobeat', 'Alt-Rock', 'New'
  ];

  blocks: GenreBlock[] = [
    { id: 1, title: 'EDM', start: 0, duration: 60, row: 0 },
    { id: 2, title: 'Techno', start: 50, duration: 30, row: 1 },
    { id: 3, title: 'House', start: 80, duration: 30, row: 0 },
  ];

  draggingBlock: GenreBlock | null = null;
  resizingBlock: GenreBlock | null = null;
  startX = 0;
  startValue = 0;
  wasDragged = false;
  selectedBlock: GenreBlock | null = null;

  isDraggingOutside = false;

  get ticks(): number[] {
    const tickArray = [];
    for (let i = 0; i <= this.totalMinutes; i += 10) {
      tickArray.push(i);
    }
    return tickArray;
  }

  getLeft(block: GenreBlock): string { return (block.start / this.totalMinutes) * 100 + '%'; }
  getWidth(block: GenreBlock): string { return (block.duration / this.totalMinutes) * 100 + '%'; }

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
        }
        else if (aStart <= bStart && aEnd < bEnd) {
          b.start = aEnd;
          b.duration = bEnd - aEnd;
        }
        else if (aStart > bStart && aEnd >= bEnd) {
          b.duration = aStart - bStart;
        }
        else if (aStart > bStart && aEnd < bEnd) {
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
      alert('Timeline is full!');
      return;
    }
    this.blocks.push({
      id: Date.now(),
      title: 'New',
      start: maxEnd,
      duration: 5,
      row: row
    });
  }
}

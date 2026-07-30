import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

interface TimelinePhaseDto {
  genre: string;
  durationMinutes: number;
  transitionOutMinutes: number;
}

interface UserContextDto {
  tempo: number;
  location: string;
  startTime: string;
  timeline: {
    phases: TimelinePhaseDto[];
  };
  songCooldownMinutes: number;
}

@Injectable({
  providedIn: 'root',
})
export class ContextApiService {
  private readonly baseUrl = 'http://127.0.0.1:8081';

  constructor(private readonly http: HttpClient) {}

  sendDummyContext(): Observable<{ status: string }> {
    const payload: UserContextDto = {
      tempo: 124,
      location: 'Party',
      startTime: '21:00:00',
      timeline: {
        phases: [
          { genre: 'EDM', durationMinutes: 45, transitionOutMinutes: 5 },
          { genre: 'HIP_HOP', durationMinutes: 35, transitionOutMinutes: 5 },
        ],
      },
      songCooldownMinutes: 30,
    };

    return this.http.post<{ status: string }>(`${this.baseUrl}/api/context`, payload);
  }
}

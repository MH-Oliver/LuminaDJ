import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
export interface TimelinePhaseDto {
  genre: string;
  durationMinutes: number;
  transitionOutMinutes: number;
}

export interface UserContextDto {
  tempo: number;
  location: string;
  startTime: string;
  timeline: {
    phases: TimelinePhaseDto[];
  };
  songCooldownMinutes: number;
  totalMinutes: number; // NEU: Hält die ausgewählte Gesamtlänge der Session
}

@Injectable({
  providedIn: 'root',
})
export class ContextApiService {


  // Zeigt jetzt auf den Standard-Port von Spring Boot
  private readonly baseUrl = 'http://127.0.0.1:8080';

  constructor(private readonly http: HttpClient) {}

  // ==========================================
  // MUSIC ENDPOINTS
  // ==========================================
  connectSpotify(): Observable<{ success: boolean }> {
    return this.http.get<{ success: boolean }>(`${this.baseUrl}/music/connectSpotify`);
  }

  loadPresets(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/music/loadPresets`);
  }

  selectPreset(name: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/music/selectPreset?name=${encodeURIComponent(name)}`);
  }

  loadGenre(query: string = ''): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/music/loadGenre?query=${encodeURIComponent(query)}`);
  }

  // ==========================================
  // VISION ENDPOINTS
  // ==========================================
  deviceFound(): Observable<{ ip: string }> {
    return this.http.get<{ ip: string }>(`${this.baseUrl}/vision/deviceFound`);
  }

  selectedDevice(ipAddress: string): Observable<{ connectedIp: string }> {
    return this.http.post<{ connectedIp: string }>(`${this.baseUrl}/vision/selectedDevice`, { ip: ipAddress });
  }

  // ==========================================
  // CONTEXT ENDPOINTS
  // ==========================================
  sendContext(payload: UserContextDto): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`${this.baseUrl}/api/context`, payload);
  }

  // NEU: Aktuellen Context für den Edit-Modus laden
  getCurrentContext(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/api/context/current`);
  }

  // ==========================================
  // SESSION ENDPOINTS
  // ==========================================

// NEU: Der echte Endpoint zum Backend
  toggleFavorite(isFavorite: boolean): Observable<{ success: boolean }> {
    return this.http.post<{ success: boolean }>(`${this.baseUrl}/session/favorite`, { isFavorite });
  }

  updateSession(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/session/update`);
  }

  skipSong(): Observable<{ nextSong: string }> {
    return this.http.post<{ nextSong: string }>(`${this.baseUrl}/session/skipSong`, {});
  }

  editSession(): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/session/edit`, {});
  }

  cancelSession(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/session/cancel`, {});
  }



  togglePlayPause(): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/session/playPause`, {});
  }

  seek(positionMs: number): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/session/seek`, { position: positionMs });
  }

  previousSong(): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/session/previous`, {});
  }


}

import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subject, Observable } from 'rxjs';

export interface SeatLockMessage {
  matchName: string;
  seatId: string;
  userId: string;
  status: 'locked' | 'available' | 'booked';
}

const API = 'http://localhost:8082';

/**
 * Manages the SSE stream for real-time seat updates and REST calls for
 * locking, unlocking, and booking seats.
 *
 * The SSE stream is NOT opened in the constructor — it is opened by
 * AuthService after the user is verified (concatMap chain). This ensures
 * the userId is always set before any seat operations.
 */
@Injectable({ providedIn: 'root' })
export class SeatService implements OnDestroy {

  private _userId: string = '';
  private _matchName: string = '';
  private eventSource?: EventSource;
  private seatUpdates$ = new Subject<SeatLockMessage>();

  readonly updates$ = this.seatUpdates$.asObservable();

  constructor(private http: HttpClient) {}

  // ── Stream lifecycle ────────────────────────────────────────────────────

  connectStream(matchName: string, userId: string): void {
    if (this.eventSource && this._matchName === matchName) return;

    this._userId = userId;
    this._matchName = matchName;
    this.disconnectStream(); // close any existing connection

    this.eventSource = new EventSource(`${API}/api/seats/${matchName}/stream`);

    this.eventSource.addEventListener('seat-update', (event: MessageEvent) => {
      const update: SeatLockMessage = JSON.parse(event.data);
      this.seatUpdates$.next(update);
    });

    this.eventSource.onerror = () => {
      // Reconnect after 3 seconds on connection loss
      setTimeout(() => {
        if (this._userId && this._matchName) this.connectStream(this._matchName, this._userId);
      }, 3000);
    };
  }

  disconnectStream(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = undefined;
      this._matchName = '';
    }
  }

  get userId(): string { return this._userId; }

  // ── Seat operations ─────────────────────────────────────────────────────

  lockSeat(matchName: string, seatId: string): Observable<any> {
    return this.http.post(`${API}/api/seats/${matchName}/${seatId}/lock`, {});
  }

  unlockSeat(matchName: string, seatId: string): Observable<any> {
    return this.http.post(`${API}/api/seats/${matchName}/${seatId}/unlock`, {});
  }

  bookSeat(matchName: string, seatId: string, payload: { customerName: string }): Observable<any> {
    return this.http.post(`${API}/api/seats/${matchName}/${seatId}/book`, payload);
  }

  ngOnDestroy(): void {
    this.disconnectStream();
    this.seatUpdates$.complete();
  }
}
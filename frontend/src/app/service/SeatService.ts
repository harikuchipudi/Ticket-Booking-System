import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subject, Observable } from 'rxjs';

export interface SeatLockMessage {
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
  private eventSource?: EventSource;
  private seatUpdates$ = new Subject<SeatLockMessage>();

  readonly updates$ = this.seatUpdates$.asObservable();

  constructor(private http: HttpClient) {}

  // ── Stream lifecycle ────────────────────────────────────────────────────

  /**
   * Called by AuthService after successful login/register.
   * Opens the SSE connection with the verified userId set.
   */
  connectStream(userId: string): void {
    this._userId = userId;
    this.disconnectStream(); // close any existing connection

    this.eventSource = new EventSource(`${API}/api/seats/stream`);

    this.eventSource.addEventListener('seat-update', (event: MessageEvent) => {
      const update: SeatLockMessage = JSON.parse(event.data);
      this.seatUpdates$.next(update);
    });

    this.eventSource.onerror = () => {
      // Reconnect after 3 seconds on connection loss
      setTimeout(() => {
        if (this._userId) this.connectStream(this._userId);
      }, 3000);
    };
  }

  disconnectStream(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = undefined;
    }
  }

  get userId(): string { return this._userId; }

  // ── Seat operations ─────────────────────────────────────────────────────

  lockSeat(seatId: string): Observable<any> {
    return this.http.post(`${API}/api/seats/${seatId}/lock`, {});
  }

  unlockSeat(seatId: string): Observable<any> {
    return this.http.post(`${API}/api/seats/${seatId}/unlock`, {});
  }

  /**
   * Books seats sequentially using RxJS concat — each seat is booked only
   * after the previous one completes. Replaces the old Promise.all (parallel).
   */
  bookSeat(seatId: string, matchName: string, customerName: string): Observable<any> {
    return this.http.post(`${API}/api/seats/${seatId}/book`, { matchName, customerName });
  }

  ngOnDestroy(): void {
    this.disconnectStream();
    this.seatUpdates$.complete();
  }
}
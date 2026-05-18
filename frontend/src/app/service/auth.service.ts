import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { concatMap, tap } from 'rxjs/operators';
import { User, AuthRequest, AuthResponse } from '../models/user';
import { SeatService } from './SeatService';

const API = 'http://localhost:8082';
const TOKEN_KEY = 'ticket_jwt';

/**
 * AuthService orchestrates the sign-in pipeline using RxJS concatMap.
 *
 * The login/register flow is deliberately sequential:
 *
 *   Step 1 — POST /api/auth/login
 *             ↓ token stored in localStorage
 *             ↓ concatMap (waits for step 1 to complete)
 *   Step 2 — GET /api/auth/me
 *             ↓ verifies token is valid, fetches user profile
 *             ↓ concatMap (waits for step 2 to complete)
 *   Step 3 — connectStream(userId)
 *             SSE stream opens with the confirmed userId
 *
 * concatMap ensures each step fully completes before the next begins.
 * If step 1 fails (wrong password) → step 2 never fires.
 * If step 2 fails (bad token) → SSE never opens.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  currentUser$ = new BehaviorSubject<User | null>(null);

  constructor(private http: HttpClient, private seatService: SeatService) {
    // Restore session on page refresh if token exists and is not expired
    const token = this.getToken();
    if (token && !this.isTokenExpired(token)) {
      this.fetchMe().subscribe({
        error: () => this.clearToken()
      });
    }
  }

  // ── Public auth actions ──────────────────────────────────────────────────

  login(email: string, password: string): Observable<User> {
    return this.http.post<AuthResponse>(`${API}/api/auth/login`, { email, password } as AuthRequest).pipe(
      // Step 1: store the JWT
      tap(res => this.storeToken(res.token)),
      // Step 2: verify token by calling /me — gets the full user profile
      concatMap(() => this.fetchMe())
    );
  }

  register(email: string, password: string, displayName: string): Observable<User> {
    return this.http.post<AuthResponse>(`${API}/api/auth/register`, { email, password, displayName } as AuthRequest).pipe(
      tap(res => this.storeToken(res.token)),
      concatMap(() => this.fetchMe())
    );
  }

  logout(): void {
    this.clearToken();
    this.currentUser$.next(null);
    this.seatService.disconnectStream();
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    return !!token && !this.isTokenExpired(token);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  // ── Private helpers ──────────────────────────────────────────────────────

  private fetchMe(): Observable<User> {
    return this.http.get<User>(`${API}/api/auth/me`).pipe(
      tap(user => this.currentUser$.next(user))
    );
  }

  private storeToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
  }

  private clearToken(): void {
    localStorage.removeItem(TOKEN_KEY);
  }

  private isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000 < Date.now();
    } catch {
      return true;
    }
  }
}

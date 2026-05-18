import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Ticket {
  id?: number;
  matchName: string;
  seat: string;
  customerName: string;
}

@Injectable({ providedIn: 'root' })
export class BookingService {
  private base = '/api/tickets';

  constructor(private http: HttpClient) {}

  getTickets(): Observable<Ticket[]> {
    return this.http.get<Ticket[]>(this.base);
  }

  createTicket(t: Ticket): Observable<Ticket> {
    return this.http.post<Ticket>(this.base, t);
  }
}

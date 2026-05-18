import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../service/auth.service';
import { HttpClient } from '@angular/common/http';
import { User } from '../models/user';
import { environment } from '../../environments/environment';

interface BookedTicket {
  id: number;
  matchName: string;
  seat: string;
  customerName: string;
  status: string;
  bookedAt: string;
}

const API = environment.apiUrl;

/**
 * Refactored from a manual booking form to a "My Bookings" panel.
 * Displays the authenticated user's info and their booked tickets.
 * Booking now flows through the seat map → lock → book pipeline.
 */
@Component({
  selector: 'app-booking',
  templateUrl: './booking.component.html',
  styleUrls: ['./booking.component.css']
})
export class BookingComponent implements OnInit {
  user: User | null = null;
  tickets: BookedTicket[] = [];

  constructor(
    private authService: AuthService,
    private http: HttpClient,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(u => {
      this.user = u;
      if (u) this.loadTickets();
    });
  }

  loadTickets(): void {
    this.http.get<BookedTicket[]>(`${API}/api/tickets/my`).subscribe({
      next: t => this.tickets = t,
      error: () => this.tickets = []
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}

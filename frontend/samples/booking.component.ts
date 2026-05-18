import { Component } from '@angular/core';
import { BookingService, Ticket } from './booking.service';

@Component({
  selector: 'app-booking',
  templateUrl: './booking.component.html'
})
export class BookingComponent {
  tickets: Ticket[] = [];
  model: Partial<Ticket> = { matchName: '', seat: '', customerName: '' };

  constructor(private svc: BookingService) {
    this.load();
  }

  load() {
    this.svc.getTickets().subscribe(x => this.tickets = x);
  }

  submit() {
    const t: Ticket = { matchName: this.model.matchName!, seat: this.model.seat!, customerName: this.model.customerName! };
    this.svc.createTicket(t).subscribe(() => {
      this.model = { matchName: '', seat: '', customerName: '' };
      this.load();
    });
  }
}

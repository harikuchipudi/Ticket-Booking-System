import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Seat } from '../models/seat';
import { SeatService } from '../service/SeatService';
import { AuthService } from '../service/auth.service';
import { concat, timer } from 'rxjs';
import { concatMap, toArray } from 'rxjs/operators';

type ModalStep = 'form' | 'processing' | 'success' | 'error';

/**
 * Mock payment modal — simulates a payment flow without a real payment processor.
 *
 * Flow:
 *   1. Show seat summary + fake card form
 *   2. User clicks "Pay" → 2-second simulated processing delay (timer(2000))
 *   3. Calls the real bookSeat API sequentially via concatMap
 *   4. Shows success screen, then emits (confirmed) after 1.5s
 *
 * To integrate a real payment provider (e.g. Stripe) later:
 *   - Replace timer(2000) with a Stripe PaymentIntent API call
 *   - Add card element in the form step
 *   - Call the booking API only after payment succeeds
 */
@Component({
  selector: 'app-payment-modal',
  templateUrl: './payment-modal.component.html',
  styleUrls: ['./payment-modal.component.css']
})
export class PaymentModalComponent implements OnInit {
  @Input() seats: Seat[] = [];
  @Input() total = 0;
  @Input() matchName = 'General Admission';

  @Output() confirmed = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  step: ModalStep = 'form';
  errorMsg = '';

  // Fake card fields — no real validation
  cardNumber = '';
  expiry = '';
  cvv = '';
  cardholderName = '';

  pricing: Record<string, number> = {
    VIP: 5000, Premium: 3000, Standard: 1500, Economy: 800
  };

  constructor(private seatService: SeatService, private authService: AuthService) {}

  ngOnInit() {
    // Pre-fill cardholder name from authenticated user
    const user = this.authService.currentUser$.value;
    if (user) this.cardholderName = user.displayName;
  }

  /** Format card number display with spaces every 4 digits */
  formatCard(value: string): void {
    this.cardNumber = value.replace(/\D/g, '').substring(0, 16).replace(/(.{4})/g, '$1 ').trim();
  }

  /** Format expiry MM/YY */
  formatExpiry(value: string): void {
    const digits = value.replace(/\D/g, '').substring(0, 4);
    this.expiry = digits.length > 2 ? `${digits.substring(0, 2)}/${digits.substring(2)}` : digits;
  }

  pay(): void {
    const user = this.authService.currentUser$.value;
    if (!user) return;

    this.step = 'processing';

    const bookRequests = this.seats.map(seat =>
      this.seatService.bookSeat(this.matchName, seat.id, { customerName: user.displayName })
    );

    // Simulate payment delay, then book seats sequentially
    timer(2000).pipe(
      concatMap(() => concat(...bookRequests).pipe(toArray()))
    ).subscribe({
      next: () => {
        this.step = 'success';
        // Auto-close after showing success
        setTimeout(() => this.confirmed.emit(), 1800);
      },
      error: (err) => {
        this.step = 'error';
        this.errorMsg = err?.error?.reason ?? 'Booking failed. Please try again.';
      }
    });
  }

  close(): void {
    // Don't allow closing while payment is processing
    if (this.step !== 'processing') {
      this.cancelled.emit();
    }
  }

  get seatsBreakdown(): { id: string; category: string; price: number }[] {
    return this.seats.map(s => ({
      id: s.id,
      category: s.category,
      price: this.pricing[s.category] ?? 0
    }));
  }

  retry(): void {
    this.step = 'form';
    this.errorMsg = '';
  }
}

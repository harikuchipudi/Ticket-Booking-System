import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Seat, SeatCategory } from '../models/seat';
import { SeatService } from '../service/SeatService';
import { AuthService } from '../service/auth.service';
import { ToastService } from '../service/toast.service';
import { Subscription } from 'rxjs';

interface RingConfig {
  category: SeatCategory;
  radius: number;
  count: number;
  label: string;
}

@Component({
  selector: 'app-stadium',
  template: `
    <div class="app-shell">

      <!-- ── Left: user panel ────────────────────────── -->
      <aside class="app-left">
        <app-booking></app-booking>
      </aside>

      <!-- ── Right: stadium area ────────────────────── -->
      <div class="app-right">

        <!-- Top bar -->
        <header class="topbar">
          <div class="topbar-brand" routerLink="/matches" style="cursor: pointer; display: flex; align-items: center; gap: 0.5rem; background: rgba(255,255,255,0.05); padding: 0.5rem 1rem; border-radius: 8px;">
            <span class="topbar-icon" style="font-size: 1.2rem;">←</span>
            <span class="topbar-name">Matches</span>
          </div>
          <div class="topbar-center">
            <span class="topbar-event">{{ matchName }}</span>
          </div>
          <div class="topbar-right">
            <span class="live-pill">
              <span class="live-dot"></span>LIVE
            </span>
            <span class="seat-count">{{ seats.length - bookedCount }} available</span>
          </div>
        </header>

        <!-- Stadium SVG -->
        <div class="stadium-wrapper">

          <svg [attr.viewBox]="'0 0 700 700'" xmlns="http://www.w3.org/2000/svg"
               style="width:100%;height:100%;display:block">

            <!-- Segment rings -->
            <g *ngFor="let ring of segmentRings; let i = index">
              <circle [attr.cx]="center.x" [attr.cy]="center.y"
                      [attr.r]="ring.diameter/2"
                      [attr.fill]="ring.color" stroke="rgba(255,255,255,0.05)" stroke-width="1"/>
              <text [attr.x]="center.x" [attr.y]="center.y - ring.diameter/2 + 14"
                    text-anchor="middle" fill="rgba(255,255,255,0.2)"
                    font-size="9" font-family="Inter,sans-serif" letter-spacing="2">
                {{ ring.label }}
              </text>
            </g>

            <!-- Pitch -->
            <ellipse [attr.cx]="center.x" [attr.cy]="center.y" rx="75" ry="50"
                     fill="rgba(34,197,94,0.15)" stroke="rgba(34,197,94,0.3)" stroke-width="1"/>
            <text [attr.x]="center.x" [attr.y]="center.y + 5"
                  text-anchor="middle" fill="rgba(34,197,94,0.5)"
                  font-size="10" font-family="Inter,sans-serif">⚽ PITCH</text>

            <!-- Seats -->
            <circle *ngFor="let seat of seats"
                    [attr.cx]="seat.x" [attr.cy]="seat.y" r="6"
                    [class]="getSeatClass(seat)"
                    (click)="selectSeat(seat)"
                    [attr.id]="'seat-' + seat.id">
              <title>{{ seat.id }} · {{ seat.category }} · {{ seat.status }}{{ seat.status === 'available' ? ' · ₹' + pricing[seat.category] : '' }}</title>
            </circle>

          </svg>

          <!-- Legend -->
          <div class="legend">
            <div class="legend-item"><span class="legend-dot vip-dot"></span>VIP ₹5K</div>
            <div class="legend-item"><span class="legend-dot premium-dot"></span>Premium ₹3K</div>
            <div class="legend-item"><span class="legend-dot standard-dot"></span>Standard ₹1.5K</div>
            <div class="legend-item"><span class="legend-dot economy-dot"></span>Economy ₹800</div>
            <div class="legend-sep"></div>
            <div class="legend-item"><span class="legend-dot locked-dot"></span>Locked</div>
            <div class="legend-item"><span class="legend-dot booked-dot"></span>Booked</div>
          </div>

          <!-- Booking bar (appears when seats selected) -->
          <div class="booking-bar" *ngIf="selectedSeats.length > 0">
            <div class="bar-left">
              <span class="bar-count">{{ selectedSeats.length }} seat{{ selectedSeats.length > 1 ? 's' : '' }}</span>
              <span class="bar-seats">{{ selectedSeatIds }}</span>
            </div>
            <div class="bar-right">
              <span class="bar-total">₹{{ total | number }}</span>
              <button class="book-btn" (click)="bookSeats()" [disabled]="booking" id="btn-book-seats">
                {{ booking ? '⏳ Processing…' : '🎫 Book Now' }}
              </button>
            </div>
          </div>

        </div><!-- /stadium-wrapper -->
      </div><!-- /app-right -->
    </div><!-- /app-shell -->

    <!-- Payment modal -->
    <app-payment-modal
      *ngIf="showPaymentModal"
      [seats]="selectedSeats"
      [total]="total"
      [matchName]="matchName"
      (confirmed)="onPaymentConfirmed()"
      (cancelled)="onPaymentCancelled()">
    </app-payment-modal>

    <!-- Global toasts -->
    <app-toast></app-toast>
  `,
  styleUrls: ['./stadium.component.css']
})
export class StadiumComponent implements OnInit, OnDestroy {

  center = { x: 350, y: 350 };
  seats: Seat[] = [];
  selectedSeats: Seat[] = [];
  booking = false;
  showPaymentModal = false;
  matchName = '';

  private updateSub?: Subscription;
  private routeSub?: Subscription;

  pricing: Record<SeatCategory, number> = {
    VIP: 5000, Premium: 3000, Standard: 1500, Economy: 800
  };

  total = 0;

  rings: RingConfig[] = [
    { category: 'VIP',      radius: 120, count: 30, label: 'VIP'      },
    { category: 'Premium',  radius: 180, count: 50, label: 'PREMIUM'  },
    { category: 'Standard', radius: 240, count: 70, label: 'STANDARD' },
    { category: 'Economy',  radius: 300, count: 90, label: 'ECONOMY'  }
  ];

  segmentRings: { diameter: number; color: string; label: string }[] = [];

  constructor(
    private route: ActivatedRoute,
    private seatService: SeatService,
    private authService: AuthService,
    private toast: ToastService
  ) {}

  ngOnInit() {
    this.buildSegmentRings();

    this.routeSub = this.route.paramMap.subscribe(params => {
      const mn = params.get('matchName');
      if (mn && mn !== this.matchName) {
        this.matchName = mn;
        this.selectedSeats = [];
        this.total = 0;
        this.generateSeats(); // Reset all seats to 'available'
        if (this.authService.isAuthenticated()) {
          // If auth is not ready, we rely on AuthService logic or just try.
          // Since auth is a requirement to view stadium, it should be present.
          const user = this.authService.currentUser$.getValue();
          if (user) {
            this.seatService.connectStream(this.matchName, user.userId);
          }
        }
      }
    });

    this.updateSub = this.seatService.updates$.subscribe(update => {
      if (update.matchName !== this.matchName) return; // ignore other matches
      const seat = this.seats.find(s => s.id === update.seatId);
      if (seat) {
        if (update.status === 'booked' || update.userId !== this.seatService.userId) {
          seat.status = update.status as any;
        }
      }
    });
  }

  ngOnDestroy() {
    this.updateSub?.unsubscribe();
    this.routeSub?.unsubscribe();
    this.seatService.disconnectStream();
  }

  buildSegmentRings() {
    const colors = [
      'rgba(250,204,21,0.08)',
      'rgba(96,165,250,0.07)',
      'rgba(74,222,128,0.06)',
      'rgba(148,163,184,0.04)'
    ];
    this.segmentRings = this.rings.map((ring, i) => ({
      diameter: ring.radius * 2 + 28,
      color: colors[i],
      label: ring.label
    }));
  }

  generateSeats() {
    this.seats = [];
    let id = 1;
    this.rings.forEach(ring => {
      for (let i = 0; i < ring.count; i++) {
        const angle = (i / ring.count) * 2 * Math.PI;
        this.seats.push({
          id: `S${id++}`,
          category: ring.category,
          x: this.center.x + ring.radius * Math.cos(angle),
          y: this.center.y + ring.radius * Math.sin(angle),
          status: 'available'
        });
      }
    });
  }

  getSeatClass(seat: Seat): string {
    return `seat ${seat.status} ${seat.category.toLowerCase()}`;
  }

  get selectedSeatIds(): string {
    return this.selectedSeats.map(s => s.id).join(', ');
  }

  get bookedCount(): number {
    return this.seats.filter(s => s.status === 'booked').length;
  }

  selectSeat(seat: Seat) {
    if (seat.status === 'booked') return;

    if (seat.status === 'available') {
      seat.status = 'locked';
      this.selectedSeats.push(seat);
      this.seatService.lockSeat(this.matchName, seat.id).subscribe({
        error: (err) => {
          seat.status = 'available';
          this.selectedSeats = this.selectedSeats.filter(s => s.id !== seat.id);
          this.calculateTotal();
          const reason = err?.status === 429
            ? 'Too many requests. Wait a moment.'
            : (err?.error?.reason ?? 'Seat already locked by someone else.');
          this.toast.warning(`Seat ${seat.id}: ${reason}`);
        }
      });
    } else if (seat.status === 'locked') {
      seat.status = 'available';
      this.selectedSeats = this.selectedSeats.filter(s => s.id !== seat.id);
      this.seatService.unlockSeat(this.matchName, seat.id).subscribe();
    }

    this.calculateTotal();
  }

  calculateTotal() {
    this.total = this.selectedSeats.reduce((sum, s) => sum + this.pricing[s.category], 0);
  }

  bookSeats() {
    if (this.selectedSeats.length === 0) return;
    this.showPaymentModal = true;
  }

  onPaymentConfirmed() {
    const count = this.selectedSeats.length;
    this.selectedSeats.forEach(s => s.status = 'booked');
    this.selectedSeats = [];
    this.total = 0;
    this.showPaymentModal = false;
    this.toast.success(`🎉 ${count} ticket${count > 1 ? 's' : ''} booked successfully!`);
  }

  onPaymentCancelled() {
    this.showPaymentModal = false;
    this.toast.info('Payment cancelled. Your selected seats remain locked for 5 minutes.');
  }
}
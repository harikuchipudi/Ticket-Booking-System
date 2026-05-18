import { Component, OnInit, OnDestroy } from '@angular/core';
import { Seat, SeatCategory } from '../models/seat';
import { SeatService } from '../service/SeatService';
import { AuthService } from '../service/auth.service';
import { Subscription } from 'rxjs';
import { concat } from 'rxjs';
import { toArray } from 'rxjs/operators';

interface RingConfig {
  category: SeatCategory;
  radius: number;
  count: number;
  label: string;
  color: string;
}

@Component({
  selector: 'app-stadium',
  template: `
    <div class="app-shell">
      <div class="app-left"><app-booking></app-booking></div>
      <div class="app-right">
        <div class="stadium-wrapper">
          <ng-container *ngTemplateOutlet="stadiumContent"></ng-container>
        </div>
      </div>
    </div>

    <ng-template #stadiumContent>
      <svg [attr.viewBox]="'0 0 700 700'" xmlns="http://www.w3.org/2000/svg"
           style="width:100%;height:100%;display:block">

        <!-- Segment rings -->
        <g *ngFor="let ring of segmentRings">
          <circle [attr.cx]="center.x" [attr.cy]="center.y"
                  [attr.r]="ring.diameter/2"
                  [attr.fill]="ring.color" [attr.stroke]="ring.stroke"
                  stroke-width="1"/>
          <text [attr.x]="center.x" [attr.y]="center.y - ring.diameter/2 + 14"
                text-anchor="middle" fill="rgba(255,255,255,0.25)"
                font-size="9" font-family="Inter,sans-serif" letter-spacing="2">
            {{ ring.label }}
          </text>
        </g>

        <!-- Pitch -->
        <ellipse [attr.cx]="center.x" [attr.cy]="center.y" rx="75" ry="50"
                 fill="rgba(34,197,94,0.15)" stroke="rgba(34,197,94,0.3)" stroke-width="1"/>
        <text [attr.x]="center.x" [attr.y]="center.y + 5"
              text-anchor="middle" fill="rgba(34,197,94,0.5)"
              font-size="10" font-family="Inter,sans-serif">PITCH</text>

        <!-- Seats -->
        <circle *ngFor="let seat of seats"
                [attr.cx]="seat.x" [attr.cy]="seat.y" r="6"
                [class]="'seat seat-' + seat.status + ' seat-cat-' + seat.category"
                (click)="selectSeat(seat)"
                [attr.id]="'seat-' + seat.id">
          <title>{{ seat.id }} ({{ seat.category }}) — {{ seat.status }}</title>
        </circle>
      </svg>

      <!-- Legend + booking bar -->
      <div class="controls">
        <div class="legend">
          <span class="dot vip"></span>VIP
          <span class="dot premium"></span>Premium
          <span class="dot standard"></span>Standard
          <span class="dot economy"></span>Economy
          <span class="dot locked"></span>Locked
          <span class="dot booked"></span>Booked
        </div>
        <div class="booking-bar" *ngIf="selectedSeats.length > 0">
          <span class="selected-count">{{ selectedSeats.length }} seat(s) selected</span>
          <span class="total-price">₹{{ total | number }}</span>
          <button class="book-btn" (click)="bookSeats()" [disabled]="booking" id="btn-book-seats">
            {{ booking ? 'Booking…' : '🎫 Book Now' }}
          </button>
        </div>
      </div>
    </ng-template>
  `,
  styleUrls: ['./stadium.component.css']
})
export class StadiumComponent implements OnInit, OnDestroy {

  center = { x: 350, y: 350 };
  seats: Seat[] = [];
  selectedSeats: Seat[] = [];
  booking = false;

  private updateSub?: Subscription;

  pricing: Record<SeatCategory, number> = {
    VIP: 5000, Premium: 3000, Standard: 1500, Economy: 800
  };

  total = 0;

  rings: RingConfig[] = [
    { category: 'VIP',      radius: 120, count: 30, label: 'VIP',      color: 'rgba(250,204,21,0.12)' },
    { category: 'Premium',  radius: 180, count: 50, label: 'PREMIUM',  color: 'rgba(96,165,250,0.10)' },
    { category: 'Standard', radius: 240, count: 70, label: 'STANDARD', color: 'rgba(74,222,128,0.08)' },
    { category: 'Economy',  radius: 300, count: 90, label: 'ECONOMY',  color: 'rgba(148,163,184,0.06)' }
  ];

  segmentRings: { diameter: number; color: string; stroke: string; label: string }[] = [];

  constructor(private seatService: SeatService, private authService: AuthService) {}

  ngOnInit() {
    this.generateSeats();
    this.buildSegmentRings();

    this.updateSub = this.seatService.updates$.subscribe(update => {
      const seat = this.seats.find(s => s.id === update.seatId);
      if (seat) {
        // Apply all remote updates; booked seats are always updated regardless of owner
        if (update.status === 'booked' || update.userId !== this.seatService.userId) {
          seat.status = update.status as any;
        }
      }
    });
  }

  ngOnDestroy() {
    this.updateSub?.unsubscribe();
  }

  buildSegmentRings() {
    this.segmentRings = this.rings.map(ring => ({
      diameter: ring.radius * 2 + 28,
      color: ring.color,
      stroke: 'rgba(255,255,255,0.05)',
      label: ring.label
    }));
  }

  generateSeats() {
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

  selectSeat(seat: Seat) {
    if (seat.status === 'booked') return;

    if (seat.status === 'available') {
      seat.status = 'locked';
      this.selectedSeats.push(seat);
      this.seatService.lockSeat(seat.id).subscribe({
        error: () => {
          seat.status = 'available';
          this.selectedSeats = this.selectedSeats.filter(s => s.id !== seat.id);
          this.calculateTotal();
        }
      });
    } else if (seat.status === 'locked') {
      seat.status = 'available';
      this.selectedSeats = this.selectedSeats.filter(s => s.id !== seat.id);
      this.seatService.unlockSeat(seat.id).subscribe();
    }

    this.calculateTotal();
  }

  calculateTotal() {
    this.total = this.selectedSeats.reduce((sum, s) => sum + this.pricing[s.category], 0);
  }

  bookSeats() {
    const user = this.authService.currentUser$.value;
    if (!user || this.selectedSeats.length === 0) return;

    this.booking = true;

    // Use concat to book seats SEQUENTIALLY — one at a time, in order
    const bookRequests = this.selectedSeats.map(seat =>
      this.seatService.bookSeat(seat.id, 'General Admission', user.displayName)
    );

    concat(...bookRequests).pipe(toArray()).subscribe({
      next: () => {
        this.selectedSeats.forEach(s => s.status = 'booked');
        this.selectedSeats = [];
        this.total = 0;
        this.booking = false;
      },
      error: () => { this.booking = false; }
    });
  }
}
export interface Seat {
  id: string;
  category: SeatCategory
  x: number;
  y: number;
  status: 'available' | 'locked' | 'booked';
}

export type SeatCategory = 'VIP' | 'Premium' | 'Standard' | 'Economy';
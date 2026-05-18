import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
  id: number;
  message: string;
  type: ToastType;
}

/**
 * Global toast notification service.
 * Inject and call .success(), .error(), .warning(), or .info() from any component.
 * Auto-dismisses after the specified duration.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private _toasts = new BehaviorSubject<Toast[]>([]);
  readonly toasts$ = this._toasts.asObservable();
  private nextId = 0;

  success(message: string, duration = 3500) { this.show(message, 'success', duration); }
  error(message: string, duration = 5000)   { this.show(message, 'error',   duration); }
  warning(message: string, duration = 4000) { this.show(message, 'warning', duration); }
  info(message: string, duration = 3000)    { this.show(message, 'info',    duration); }

  show(message: string, type: ToastType = 'info', duration = 3500) {
    const id = this.nextId++;
    this._toasts.next([...this._toasts.value, { id, message, type }]);
    setTimeout(() => this.dismiss(id), duration);
  }

  dismiss(id: number) {
    this._toasts.next(this._toasts.value.filter(t => t.id !== id));
  }
}

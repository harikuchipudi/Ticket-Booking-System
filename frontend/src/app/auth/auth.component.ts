import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../service/auth.service';

@Component({
  selector: 'app-auth',
  templateUrl: './auth.component.html',
  styleUrls: ['./auth.component.css']
})
export class AuthComponent {
  mode: 'login' | 'register' = 'login';

  email       = '';
  password    = '';
  displayName = '';
  errorMsg    = '';
  loading     = false;

  constructor(private authService: AuthService, private router: Router) {}

  switchMode(m: 'login' | 'register') {
    this.mode = m;
    this.errorMsg = '';
  }

  submit() {
    this.errorMsg = '';
    this.loading  = true;

    const action$ = this.mode === 'login'
      ? this.authService.login(this.email, this.password)
      : this.authService.register(this.email, this.password, this.displayName);

    // The concatMap chain inside AuthService fires here:
    //   POST /login (or /register) → store token → GET /me → connectStream
    action$.subscribe({
      next: () => this.router.navigate(['/stadium']),
      error: (err) => {
        this.loading  = false;
        this.errorMsg = err?.error?.message ?? 'Something went wrong. Please try again.';
      }
    });
  }
}

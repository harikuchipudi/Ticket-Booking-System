import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

export interface Match {
  id: string;
  name: string;
  team1: string;
  team2: string;
  venue: string;
  date: string;
  status: string;
}

import { Router } from '@angular/router';
import { AuthService } from '../service/auth.service';

@Component({
  selector: 'app-matches',
  templateUrl: './matches.component.html',
  styleUrls: ['./matches.component.css']
})
export class MatchesComponent implements OnInit {
  matches: Match[] = [];
  loading = true;
  private API = environment.apiUrl;

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private router: Router
  ) {}

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  ngOnInit() {
    this.http.get<Match[]>(`${this.API}/api/matches`).subscribe({
      next: (data) => {
        this.matches = data;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }
}

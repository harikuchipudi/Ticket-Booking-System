import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface Match {
  id: string;
  name: string;
  team1: string;
  team2: string;
  venue: string;
  date: string;
  status: string;
}

@Component({
  selector: 'app-matches',
  templateUrl: './matches.component.html',
  styleUrls: ['./matches.component.css']
})
export class MatchesComponent implements OnInit {
  matches: Match[] = [];
  loading = true;

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.http.get<Match[]>('http://localhost:8082/api/matches').subscribe({
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

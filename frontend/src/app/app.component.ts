import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  template: `
    <router-outlet></router-outlet>

    <!-- Stadium layout is shown inside StadiumComponent directly -->
  `,
  styles: [`:host { display: block; }`]
})
export class AppComponent {}

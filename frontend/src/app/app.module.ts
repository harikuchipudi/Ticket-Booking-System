import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { RouterModule, Routes } from '@angular/router';

import { AppComponent }           from './app.component';
import { AuthComponent }          from './auth/auth.component';
import { BookingComponent }       from './booking/booking.component';
import { StadiumComponent }       from './stadium/stadium.component';
import { PaymentModalComponent }  from './payment-modal/payment-modal.component';
import { ToastComponent }         from './toast/toast.component';
import { AuthGuard }              from './guards/auth.guard';
import { JwtInterceptor }         from './service/jwt.interceptor';
import { MatchesComponent } from './matches/matches.component';

const routes: Routes = [
  { path: 'login',   component: AuthComponent },
  { path: 'matches', component: MatchesComponent, canActivate: [AuthGuard] },
  { path: 'stadium/:matchName', component: StadiumComponent, canActivate: [AuthGuard] },
  { path: '',        redirectTo: 'matches', pathMatch: 'full' },
  { path: '**',      redirectTo: 'matches' }
];

@NgModule({
  declarations: [
    AppComponent,
    AuthComponent,
    BookingComponent,
    StadiumComponent,
    PaymentModalComponent,
    ToastComponent,
    MatchesComponent
  ],
  imports: [
    BrowserModule,
    FormsModule,
    HttpClientModule,
    RouterModule.forRoot(routes, { useHash: true })
  ],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: JwtInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}

import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { RouterModule, Routes } from '@angular/router';

import { AppComponent }    from './app.component';
import { AuthComponent }   from './auth/auth.component';
import { BookingComponent } from './booking/booking.component';
import { StadiumComponent } from './stadium/stadium.component';
import { AuthGuard }       from './guards/auth.guard';
import { JwtInterceptor }  from './service/jwt.interceptor';

const routes: Routes = [
  { path: 'login',   component: AuthComponent },
  { path: 'stadium', component: StadiumComponent, canActivate: [AuthGuard] },
  { path: '',        redirectTo: 'stadium', pathMatch: 'full' },
  { path: '**',      redirectTo: 'stadium' }
];

@NgModule({
  declarations: [
    AppComponent,
    AuthComponent,
    BookingComponent,
    StadiumComponent
  ],
  imports: [
    BrowserModule,
    FormsModule,
    HttpClientModule,
    RouterModule.forRoot(routes)
  ],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: JwtInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}

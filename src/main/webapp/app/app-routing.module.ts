import { DEBUG_INFO_ENABLED } from 'app/app.constants';
import { Authority } from 'app/config/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access.service';
import { errorRoute } from './layouts/error/error.route';

import HomeComponent from './home/home.component';
import NavbarComponent from './layouts/navbar/navbar.component';
import LoginComponent from './login/login.component';

import { RouterModule } from '@angular/router';
import { NgModule } from '@angular/core';
import { SimulationsOverviewComponent } from './simulations/simulations-overview/simulations-overview.component';
import { ArtemisUsersComponent } from './artemis-users/artemis-users.component';
import { UnsubscribeScheduleComponent } from './unsubscribe-schedule/unsubscribe-schedule.component';

@NgModule({
  imports: [
    RouterModule.forRoot(
      [
        {
          path: '',
          component: HomeComponent,
          title: 'Artemis-Benchmarking',
        },
        {
          path: '',
          component: NavbarComponent,
          outlet: 'navbar',
        },
        {
          path: 'admin',
          data: {
            authorities: [Authority.ADMIN],
          },
          canActivate: [UserRouteAccessService],
          loadChildren: () => import('./admin/admin-routing.module'),
        },
        {
          path: 'account',
          loadChildren: () => import('./account/account.route'),
        },
        {
          path: 'login',
          component: LoginComponent,
          title: 'Sign in',
        },
        {
          path: '',
          loadChildren: () => import(`./entities/entity-routing.module`).then(({ EntityRoutingModule }) => EntityRoutingModule),
        },
        {
          path: 'simulations',
          data: {
            authorities: [Authority.ADMIN],
          },
          component: SimulationsOverviewComponent,
          title: 'Simulations',
          canActivate: [UserRouteAccessService],
        },
        {
          path: 'artemis-users/:server',
          data: {
            authorities: [Authority.ADMIN],
          },
          component: ArtemisUsersComponent,
          title: 'Artemis Users',
          canActivate: [UserRouteAccessService],
        },
        {
          path: 'unsubscribe',
          component: UnsubscribeScheduleComponent,
          title: 'Unsubscribe',
        },
        ...errorRoute,
      ],
      { enableTracing: DEBUG_INFO_ENABLED, bindToComponentInputs: true },
    ),
  ],
  exports: [RouterModule],
})
export class AppRoutingModule {}

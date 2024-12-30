import { Routes } from '@angular/router';

import { Authority } from 'app/config/authority.constants';

import { UserRouteAccessService } from 'app/core/auth/user-route-access.service';
import { errorRoute } from './layouts/error/error.route';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./home/home.component'),
    title: 'Artemis-Benchmarking',
  },
  {
    path: '',
    loadComponent: () => import('./layouts/navbar/navbar.component'),
    outlet: 'navbar',
  },
  {
    path: 'admin',
    data: {
      authorities: [Authority.ADMIN],
    },
    canActivate: [UserRouteAccessService],
    loadChildren: () => import('./admin/admin.routes'),
  },
  {
    path: 'account',
    loadChildren: () => import('./account/account.route'),
  },
  {
    path: 'login',
    loadComponent: () => import('./login/login.component'),
    title: 'Sign in',
  },
  {
    path: 'simulations',
    data: {
      authorities: [Authority.ADMIN],
    },
    loadComponent: () => import('./simulations/simulations-overview/simulations-overview.component'),
    title: 'Simulations',
    canActivate: [UserRouteAccessService],
  },
  {
    path: 'artemis-users/:server',
    data: {
      authorities: [Authority.ADMIN],
    },
    loadComponent: () => import('./artemis-users/artemis-users.component'),
    title: 'Artemis Users',
    canActivate: [UserRouteAccessService],
  },
  {
    path: 'unsubscribe',
    loadComponent: () => import('./unsubscribe-schedule/unsubscribe-schedule.component'),
    title: 'Unsubscribe',
  },
  ...errorRoute,
];

export default routes;

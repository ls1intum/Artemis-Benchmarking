import { UserRouteAccessService } from 'app/core/auth/user-route-access.service';
import PasswordComponent from './password.component';
import { Route } from '@angular/router';

const passwordRoute: Route = {
  path: 'password',
  component: PasswordComponent,
  title: 'Password',
  canActivate: [UserRouteAccessService],
};

export default passwordRoute;

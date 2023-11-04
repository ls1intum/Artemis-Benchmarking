import PasswordResetInitComponent from './password-reset-init.component';
import { Route } from '@angular/router';

const passwordResetInitRoute: Route = {
  path: 'reset/request',
  component: PasswordResetInitComponent,
  title: 'Password',
};

export default passwordResetInitRoute;

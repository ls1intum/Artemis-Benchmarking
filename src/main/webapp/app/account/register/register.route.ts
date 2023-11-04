import RegisterComponent from './register.component';
import { Route } from '@angular/router';

const registerRoute: Route = {
  path: 'register',
  component: RegisterComponent,
  title: 'Registration',
};

export default registerRoute;

import { UserRouteAccessService } from 'app/core/auth/user-route-access.service';
import SettingsComponent from './settings.component';
import { Route } from '@angular/router';

const settingsRoute: Route = {
  path: 'settings',
  component: SettingsComponent,
  title: 'Settings',
  canActivate: [UserRouteAccessService],
};

export default settingsRoute;

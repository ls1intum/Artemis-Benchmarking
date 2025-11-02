import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: 'user-management',
    loadChildren: () => import('./user-management/user-management.route'),
    title: 'userManagement.home.title',
  },
  {
    path: 'configuration',
    loadComponent: () => import('./configuration/configuration.component'),
    title: 'Configuration',
  },
  {
    path: 'health',
    loadComponent: () => import('./health/health.component'),
    title: 'Health',
  },
  {
    path: 'logs',
    loadComponent: () => import('./logs/logs.component'),
    title: 'Logs',
  },
  {
    path: 'metrics',
    loadComponent: () => import('./metrics/metrics.component'),
    title: 'Metrics',
  },
];

export default routes;

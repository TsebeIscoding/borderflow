import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'trips/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/trip-detail/trip-detail.component').then((m) => m.TripDetailComponent),
  },
  {
    path: 'containers',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/container-dashboard/container-dashboard.component').then((m) => m.ContainerDashboardComponent),
  },
  {
    path: 'containers/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/container-detail/container-detail.component').then((m) => m.ContainerDetailComponent),
  },
  { path: '**', redirectTo: '' },
];

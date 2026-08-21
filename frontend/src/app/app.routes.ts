import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'trips/:id',
    loadComponent: () =>
      import('./features/trip-detail/trip-detail.component').then((m) => m.TripDetailComponent),
  },
  { path: '**', redirectTo: '' },
];

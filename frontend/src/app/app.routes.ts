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
  {
    path: 'vehicles',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/vehicle-dashboard/vehicle-dashboard.component').then((m) => m.VehicleDashboardComponent),
  },
  {
    path: 'vehicles/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/vehicle-detail/vehicle-detail.component').then((m) => m.VehicleDetailComponent),
  },
  {
    path: 'drivers',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/driver-dashboard/driver-dashboard.component').then((m) => m.DriverDashboardComponent),
  },
  {
    path: 'drivers/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/driver-detail/driver-detail.component').then((m) => m.DriverDetailComponent),
  },
  {
    path: 'clients',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/client-dashboard/client-dashboard.component').then((m) => m.ClientDashboardComponent),
  },
  {
    path: 'consignments',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/consignment-dashboard/consignment-dashboard.component').then((m) => m.ConsignmentDashboardComponent),
  },
  { path: '**', redirectTo: '' },
];

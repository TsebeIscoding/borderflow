import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { VehicleService } from '../../core/services/vehicle.service';
import { VehicleSummary } from '../../core/models/vehicle.model';
import { RouteRailComponent } from '../../shared/route-rail/route-rail.component';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

/** Same shape as ContainerDashboardComponent. */
@Component({
  selector: 'bf-vehicle-dashboard',
  standalone: true,
  imports: [RouterLink, RouteRailComponent, ReactiveFormsModule],
  templateUrl: './vehicle-dashboard.component.html',
  styleUrls: ['../dashboard/dashboard.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehicleDashboardComponent implements OnInit {
  private readonly vehicleService = inject(VehicleService);
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);

  readonly vehicles = signal<VehicleSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly showCreateForm = signal(false);
  readonly creating = signal(false);
  readonly createError = signal<string | null>(null);

  readonly canCreate = this.auth.isOperator && environment.siteId === 'depot';

  readonly createForm = this.fb.nonNullable.group({
    registrationNumber: ['', Validators.required],
    capacity: [0, [Validators.required, Validators.min(0.01)]],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.vehicleService.listVehicles().subscribe({
      next: (vehicles) => {
        this.vehicles.set(vehicles);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not reach this site\'s vehicle list.');
        this.loading.set(false);
      },
    });
  }

  submitCreate(): void {
    if (this.createForm.invalid) return;

    this.creating.set(true);
    this.createError.set(null);

    this.vehicleService.create(this.createForm.getRawValue()).subscribe({
      next: () => {
        this.creating.set(false);
        this.showCreateForm.set(false);
        this.createForm.reset();
        this.load();
      },
      error: (err) => {
        this.creating.set(false);
        this.createError.set(err?.error?.message ?? 'Could not create vehicle.');
      },
    });
  }
}

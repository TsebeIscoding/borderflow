import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DriverService } from '../../core/services/driver.service';
import { DriverSummary } from '../../core/models/driver.model';
import { RouteRailComponent } from '../../shared/route-rail/route-rail.component';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

/** Same shape as VehicleDashboardComponent. */
@Component({
  selector: 'bf-driver-dashboard',
  standalone: true,
  imports: [RouterLink, RouteRailComponent, ReactiveFormsModule],
  templateUrl: './driver-dashboard.component.html',
  styleUrls: ['../dashboard/dashboard.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DriverDashboardComponent implements OnInit {
  private readonly driverService = inject(DriverService);
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);

  readonly drivers = signal<DriverSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly showCreateForm = signal(false);
  readonly creating = signal(false);
  readonly createError = signal<string | null>(null);

  readonly canCreate = this.auth.isOperator && environment.siteId === 'depot';

  readonly createForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    licenseNumber: ['', Validators.required],
    phone: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.driverService.listDrivers().subscribe({
      next: (drivers) => {
        this.drivers.set(drivers);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not reach this site\'s driver list.');
        this.loading.set(false);
      },
    });
  }

  submitCreate(): void {
    if (this.createForm.invalid) return;

    this.creating.set(true);
    this.createError.set(null);

    const raw = this.createForm.getRawValue();
    this.driverService.create({ ...raw, phone: raw.phone || null }).subscribe({
      next: () => {
        this.creating.set(false);
        this.showCreateForm.set(false);
        this.createForm.reset();
        this.load();
      },
      error: (err) => {
        this.creating.set(false);
        this.createError.set(err?.error?.message ?? 'Could not create driver.');
      },
    });
  }
}

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { VehicleService } from '../../core/services/vehicle.service';
import { SITE_LABELS, SITE_ORDER, SiteId } from '../../core/models/trip.model';
import { VehicleSummary } from '../../core/models/vehicle.model';
import { RouteRailComponent } from '../../shared/route-rail/route-rail.component';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';

/** Same shape as ContainerDetailComponent. */
@Component({
  selector: 'bf-vehicle-detail',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, RouteRailComponent],
  templateUrl: './vehicle-detail.component.html',
  styleUrls: ['../trip-detail/trip-detail.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehicleDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly vehicleService = inject(VehicleService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);

  readonly vehicle = signal<VehicleSummary | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly relocateError = signal<string | null>(null);
  readonly relocateSuccess = signal<string | null>(null);
  readonly deleting = signal(false);
  readonly deleteError = signal<string | null>(null);

  readonly thisSiteId = environment.siteId;
  readonly siteLabels = SITE_LABELS;
  readonly destinationOptions = signal<SiteId[]>([]);

  readonly form = this.fb.nonNullable.group({
    toSiteId: ['', Validators.required],
    verifiedBy: ['', Validators.required],
  });

  ngOnInit(): void {
    const vehicleId = this.route.snapshot.paramMap.get('id')!;
    this.load(vehicleId);
  }

  private load(vehicleId: string): void {
    this.vehicleService.getVehicle(vehicleId).subscribe({
      next: (vehicle) => {
        this.vehicle.set(vehicle);
        this.destinationOptions.set(SITE_ORDER.filter((s) => s !== vehicle.currentSiteId));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Vehicle not found on this site\'s manifest.');
        this.loading.set(false);
      },
    });
  }

  submitRelocate(): void {
    const vehicle = this.vehicle();
    if (!vehicle || this.form.invalid) return;

    this.submitting.set(true);
    this.relocateError.set(null);
    this.relocateSuccess.set(null);

    const { toSiteId, verifiedBy } = this.form.getRawValue();

    this.vehicleService.relocate(vehicle.vehicleId, { toSiteId, verifiedBy }).subscribe({
      next: (response) => {
        this.relocateSuccess.set(`Relocated to ${this.siteLabels[response.toSiteId as SiteId]}.`);
        this.submitting.set(false);
        this.form.reset();
        this.load(vehicle.vehicleId);
      },
      error: (err) => {
        this.relocateError.set(err?.error?.message ?? 'Relocation rejected by this site.');
        this.submitting.set(false);
      },
    });
  }

  canInitiateRelocation(vehicle: VehicleSummary): boolean {
    return this.auth.isOperator && vehicle.currentSiteId === this.thisSiteId;
  }

  canDelete(): boolean {
    return this.auth.isOperator && this.thisSiteId === 'depot';
  }

  deleteVehicle(): void {
    const vehicle = this.vehicle();
    if (!vehicle) return;
    if (!confirm(`Delete vehicle ${vehicle.registrationNumber}? This cannot be undone.`)) return;

    this.deleting.set(true);
    this.deleteError.set(null);

    this.vehicleService.delete(vehicle.vehicleId).subscribe({
      next: () => this.router.navigateByUrl('/vehicles'),
      error: (err) => {
        this.deleting.set(false);
        this.deleteError.set(err?.error?.message ?? 'Could not delete vehicle.');
      },
    });
  }
}

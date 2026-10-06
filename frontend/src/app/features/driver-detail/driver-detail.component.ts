import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DriverService } from '../../core/services/driver.service';
import { SITE_LABELS, SITE_ORDER, SiteId } from '../../core/models/trip.model';
import { DriverSummary } from '../../core/models/driver.model';
import { RouteRailComponent } from '../../shared/route-rail/route-rail.component';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';

/** Same shape as VehicleDetailComponent. */
@Component({
  selector: 'bf-driver-detail',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, RouteRailComponent],
  templateUrl: './driver-detail.component.html',
  styleUrls: ['../trip-detail/trip-detail.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DriverDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly driverService = inject(DriverService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);

  readonly driver = signal<DriverSummary | null>(null);
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
    const driverId = this.route.snapshot.paramMap.get('id')!;
    this.load(driverId);
  }

  private load(driverId: string): void {
    this.driverService.getDriver(driverId).subscribe({
      next: (driver) => {
        this.driver.set(driver);
        this.destinationOptions.set(SITE_ORDER.filter((s) => s !== driver.currentSiteId));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Driver not found on this site\'s manifest.');
        this.loading.set(false);
      },
    });
  }

  submitRelocate(): void {
    const driver = this.driver();
    if (!driver || this.form.invalid) return;

    this.submitting.set(true);
    this.relocateError.set(null);
    this.relocateSuccess.set(null);

    const { toSiteId, verifiedBy } = this.form.getRawValue();

    this.driverService.relocate(driver.driverId, { toSiteId, verifiedBy }).subscribe({
      next: (response) => {
        this.relocateSuccess.set(`Relocated to ${this.siteLabels[response.toSiteId as SiteId]}.`);
        this.submitting.set(false);
        this.form.reset();
        this.load(driver.driverId);
      },
      error: (err) => {
        this.relocateError.set(err?.error?.message ?? 'Relocation rejected by this site.');
        this.submitting.set(false);
      },
    });
  }

  canInitiateRelocation(driver: DriverSummary): boolean {
    return this.auth.isOperator && driver.currentSiteId === this.thisSiteId;
  }

  canDelete(): boolean {
    return this.auth.isOperator && this.thisSiteId === 'depot';
  }

  deleteDriver(): void {
    const driver = this.driver();
    if (!driver) return;
    if (!confirm(`Delete driver ${driver.name}? This cannot be undone.`)) return;

    this.deleting.set(true);
    this.deleteError.set(null);

    this.driverService.delete(driver.driverId).subscribe({
      next: () => this.router.navigateByUrl('/drivers'),
      error: (err) => {
        this.deleting.set(false);
        this.deleteError.set(err?.error?.message ?? 'Could not delete driver.');
      },
    });
  }
}

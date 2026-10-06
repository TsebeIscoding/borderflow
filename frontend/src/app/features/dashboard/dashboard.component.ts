import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TripService } from '../../core/services/trip.service';
import { SITE_LABELS, SITE_ORDER, TripSummary } from '../../core/models/trip.model';
import { RouteRailComponent } from '../../shared/route-rail/route-rail.component';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'bf-dashboard',
  standalone: true,
  imports: [RouterLink, RouteRailComponent, ReactiveFormsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent implements OnInit {
  private readonly tripService = inject(TripService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);

  readonly trips = signal<TripSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly showCreateForm = signal(false);
  readonly creating = signal(false);
  readonly createError = signal<string | null>(null);

  readonly siteLabels = SITE_LABELS;
  readonly siteOptions = SITE_ORDER;
  /** Only Depot's backend accepts a create -- see OriginSiteOnlyException. Hiding it elsewhere is UX, the backend re-enforces regardless. */
  readonly canCreate = this.auth.isOperator && environment.siteId === 'depot';

  readonly createForm = this.fb.nonNullable.group({
    destinationSiteId: ['', Validators.required],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.tripService.listTrips().subscribe({
      next: (trips) => {
        this.trips.set(trips);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not reach this site\'s manifest service.');
        this.loading.set(false);
      },
    });
  }

  submitCreate(): void {
    if (this.createForm.invalid) return;

    this.creating.set(true);
    this.createError.set(null);

    this.tripService.create(this.createForm.getRawValue()).subscribe({
      next: (trip) => {
        this.creating.set(false);
        this.showCreateForm.set(false);
        this.createForm.reset();
        this.router.navigate(['/trips', trip.tripId]);
      },
      error: (err) => {
        this.creating.set(false);
        this.createError.set(err?.error?.message ?? 'Could not create trip.');
      },
    });
  }
}

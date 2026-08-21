import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TripService } from '../../core/services/trip.service';
import { SITE_LABELS, SITE_ORDER, SiteId, TripSummary } from '../../core/models/trip.model';
import { RouteRailComponent } from '../../shared/route-rail/route-rail.component';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'bf-trip-detail',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, RouteRailComponent],
  templateUrl: './trip-detail.component.html',
  styleUrl: './trip-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TripDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly tripService = inject(TripService);
  private readonly fb = inject(FormBuilder);

  readonly trip = signal<TripSummary | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly handoverError = signal<string | null>(null);
  readonly handoverSuccess = signal<string | null>(null);

  readonly thisSiteId = environment.siteId;
  readonly siteLabels = SITE_LABELS;

  /** Every site except the one this trip is currently at -- you cannot hand a trip to itself. */
  readonly destinationOptions = signal<SiteId[]>([]);

  readonly form = this.fb.nonNullable.group({
    toSiteId: ['', Validators.required],
    verifiedBy: ['', Validators.required],
  });

  ngOnInit(): void {
    const tripId = this.route.snapshot.paramMap.get('id')!;
    this.load(tripId);
  }

  private load(tripId: string): void {
    this.tripService.getTrip(tripId).subscribe({
      next: (trip) => {
        this.trip.set(trip);
        this.destinationOptions.set(SITE_ORDER.filter((s) => s !== trip.currentSiteId));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Trip not found on this site\'s manifest.');
        this.loading.set(false);
      },
    });
  }

  submitHandover(): void {
    const trip = this.trip();
    if (!trip || this.form.invalid) return;

    this.submitting.set(true);
    this.handoverError.set(null);
    this.handoverSuccess.set(null);

    const { toSiteId, verifiedBy } = this.form.getRawValue();

    this.tripService.handOff(trip.tripId, { toSiteId, verifiedBy }).subscribe({
      next: (response) => {
        this.handoverSuccess.set(
          `Handed off to ${this.siteLabels[response.toSiteId as SiteId]}. New status: ${response.newStatus}.`
        );
        this.submitting.set(false);
        this.form.reset();
        this.load(trip.tripId);
      },
      error: (err) => {
        const message = err?.error?.message ?? 'Handover rejected by this site.';
        this.handoverError.set(message);
        this.submitting.set(false);
      },
    });
  }

  /** This site can only initiate a handover if it currently holds the trip -- mirrors HandoverService's own rule. */
  canInitiateHandover(trip: TripSummary): boolean {
    return trip.currentSiteId === this.thisSiteId && trip.status !== 'Delivered';
  }
}

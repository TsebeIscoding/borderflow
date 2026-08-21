import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TripService } from '../../core/services/trip.service';
import { TripSummary } from '../../core/models/trip.model';
import { RouteRailComponent } from '../../shared/route-rail/route-rail.component';

@Component({
  selector: 'bf-dashboard',
  standalone: true,
  imports: [RouterLink, RouteRailComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent implements OnInit {
  private readonly tripService = inject(TripService);

  readonly trips = signal<TripSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
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
}

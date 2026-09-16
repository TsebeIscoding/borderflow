import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ContainerService } from '../../core/services/container.service';
import { ContainerSummary } from '../../core/models/container.model';
import { RouteRailComponent } from '../../shared/route-rail/route-rail.component';

/** Same shape as DashboardComponent -- see it for the reasoning behind every choice here. */
@Component({
  selector: 'bf-container-dashboard',
  standalone: true,
  imports: [RouterLink, RouteRailComponent],
  templateUrl: './container-dashboard.component.html',
  styleUrl: '../dashboard/dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainerDashboardComponent implements OnInit {
  private readonly containerService = inject(ContainerService);

  readonly containers = signal<ContainerSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.containerService.listContainers().subscribe({
      next: (containers) => {
        this.containers.set(containers);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not reach this site\'s manifest service.');
        this.loading.set(false);
      },
    });
  }
}

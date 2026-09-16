import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ContainerService } from '../../core/services/container.service';
import { SITE_LABELS, SITE_ORDER, SiteId } from '../../core/models/trip.model';
import { ContainerSummary } from '../../core/models/container.model';
import { RouteRailComponent } from '../../shared/route-rail/route-rail.component';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Same shape as TripDetailComponent, minus the "Delivered" terminal
 * state -- see ContainerRelocationService's class javadoc for why
 * Container has no equivalent concept.
 */
@Component({
  selector: 'bf-container-detail',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, RouteRailComponent],
  templateUrl: './container-detail.component.html',
  styleUrl: '../trip-detail/trip-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainerDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly containerService = inject(ContainerService);
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);

  readonly container = signal<ContainerSummary | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly relocateError = signal<string | null>(null);
  readonly relocateSuccess = signal<string | null>(null);

  readonly thisSiteId = environment.siteId;
  readonly siteLabels = SITE_LABELS;
  readonly destinationOptions = signal<SiteId[]>([]);

  readonly form = this.fb.nonNullable.group({
    toSiteId: ['', Validators.required],
    verifiedBy: ['', Validators.required],
  });

  ngOnInit(): void {
    const containerId = this.route.snapshot.paramMap.get('id')!;
    this.load(containerId);
  }

  private load(containerId: string): void {
    this.containerService.getContainer(containerId).subscribe({
      next: (container) => {
        this.container.set(container);
        this.destinationOptions.set(SITE_ORDER.filter((s) => s !== container.currentSiteId));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Container not found on this site\'s manifest.');
        this.loading.set(false);
      },
    });
  }

  submitRelocate(): void {
    const container = this.container();
    if (!container || this.form.invalid) return;

    this.submitting.set(true);
    this.relocateError.set(null);
    this.relocateSuccess.set(null);

    const { toSiteId, verifiedBy } = this.form.getRawValue();

    this.containerService.relocate(container.containerId, { toSiteId, verifiedBy }).subscribe({
      next: (response) => {
        this.relocateSuccess.set(
          `Relocated to ${this.siteLabels[response.toSiteId as SiteId]}. New status: ${response.newStatus}.`
        );
        this.submitting.set(false);
        this.form.reset();
        this.load(container.containerId);
      },
      error: (err) => {
        const message = err?.error?.message ?? 'Relocation rejected by this site.';
        this.relocateError.set(message);
        this.submitting.set(false);
      },
    });
  }

  /** Same rule as TripDetailComponent.canInitiateHandover -- see its comment. No "terminal status" check here, unlike Trip. */
  canInitiateRelocation(container: ContainerSummary): boolean {
    return this.auth.isOperator && container.currentSiteId === this.thisSiteId;
  }
}

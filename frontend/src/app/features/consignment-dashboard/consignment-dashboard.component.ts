import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ClientService } from '../../core/services/client.service';
import { DeliveryPointService } from '../../core/services/delivery-point.service';
import { DeliveryPoint } from '../../core/models/delivery-point.model';
import { CARGO_TYPES, CURRENCIES, ClientSummary, ConsignmentSummary } from '../../core/models/client.model';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

/** Same shape as ClientDashboardComponent -- see its class comment. Loads Clients too, purely to populate the create form's dropdown and to show each row's client name instead of a raw UUID. */
@Component({
  selector: 'bf-consignment-dashboard',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './consignment-dashboard.component.html',
  styleUrls: ['../dashboard/dashboard.component.css', '../client-dashboard/client-dashboard.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConsignmentDashboardComponent implements OnInit {
  private readonly clientService = inject(ClientService);
  private readonly deliveryPointService = inject(DeliveryPointService);
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);

  readonly consignments = signal<ConsignmentSummary[]>([]);
  readonly clients = signal<ClientSummary[]>([]);
  readonly deliveryPoints = signal<DeliveryPoint[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly showCreateForm = signal(false);
  readonly creating = signal(false);
  readonly createError = signal<string | null>(null);
  readonly deletingId = signal<string | null>(null);
  readonly deleteError = signal<string | null>(null);

  readonly canWrite = this.auth.isOperator && environment.siteId === 'depot';
  readonly cargoTypes = CARGO_TYPES;
  readonly currencies = CURRENCIES;

  readonly createForm = this.fb.nonNullable.group({
    clientId: ['', Validators.required],
    description: ['', Validators.required],
    deliveryPointId: ['', Validators.required],
    cargoType: ['General', Validators.required],
    totalWeightKg: [null as number | null, [Validators.required, Validators.min(0.01)]],
    packageCount: [null as number | null, [Validators.required, Validators.min(1)]],
    declaredValue: [null as number | null, Validators.min(0)],
    currency: ['LSL'],
    customsReference: [''],
    handlingNotes: [''],
    deliveryLocation: [''],
    expectedDeliveryDate: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.clientService.listConsignments().subscribe({
      next: (consignments) => {
        this.consignments.set(consignments);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not reach this site\'s consignment list.');
        this.loading.set(false);
      },
    });

    this.deliveryPointService.listDeliveryPoints().subscribe({
      next: (points) => this.deliveryPoints.set(points),
      error: () => {},
    });

    // Best-effort: only needed to render client names / populate the create dropdown.
    // If this fails, consignment rows just fall back to showing the raw clientId.
    this.clientService.listClients().subscribe({
      next: (clients) => this.clients.set(clients),
      error: () => {},
    });
  }

  deliveryPointName(deliveryPointId: string | null): string {
    if (!deliveryPointId) return 'No delivery point';
    return this.deliveryPoints().find((p) => p.deliveryPointId === deliveryPointId)?.name ?? deliveryPointId.slice(0, 8);
  }

  clientName(clientId: string): string {
    return this.clients().find((c) => c.clientId === clientId)?.name ?? clientId.slice(0, 8);
  }

  /** One line summarising the optional and cargo details, skipping anything that is empty. */
  detailLine(consignment: ConsignmentSummary): string {
    const parts: string[] = [];
    if (consignment.cargoType) parts.push(consignment.cargoType);
    if (consignment.totalWeightKg !== null) parts.push(`${consignment.totalWeightKg} kg`);
    if (consignment.packageCount !== null) parts.push(`${consignment.packageCount} pkg`);
    if (consignment.declaredValue !== null && consignment.currency) parts.push(`${consignment.declaredValue} ${consignment.currency}`);
    if (consignment.customsReference) parts.push(`customs ${consignment.customsReference}`);
    if (consignment.expectedDeliveryDate) parts.push(`due ${consignment.expectedDeliveryDate}`);
    return parts.join(' · ');
  }

  submitCreate(): void {
    if (this.createForm.invalid) return;

    const v = this.createForm.getRawValue();
    if (v.totalWeightKg === null || v.packageCount === null) return;

    // Declared value and currency go together; blank optional text is sent as null.
    const hasValue = v.declaredValue !== null;

    this.creating.set(true);
    this.createError.set(null);

    this.clientService.createConsignment({
      clientId: v.clientId,
      description: v.description,
      deliveryPointId: v.deliveryPointId,
      cargoType: v.cargoType,
      totalWeightKg: v.totalWeightKg,
      packageCount: v.packageCount,
      declaredValue: hasValue ? v.declaredValue : null,
      currency: hasValue ? v.currency : null,
      customsReference: v.customsReference.trim() || null,
      handlingNotes: v.handlingNotes.trim() || null,
      deliveryLocation: v.deliveryLocation.trim() || null,
      expectedDeliveryDate: v.expectedDeliveryDate || null,
    }).subscribe({
      next: () => {
        this.creating.set(false);
        this.showCreateForm.set(false);
        this.createForm.reset();
        this.load();
      },
      error: (err) => {
        this.creating.set(false);
        this.createError.set(err?.error?.message ?? 'Could not create consignment.');
      },
    });
  }

  deleteConsignment(consignment: ConsignmentSummary): void {
    if (!confirm(`Delete this consignment? This cannot be undone.`)) return;

    this.deletingId.set(consignment.consignmentId);
    this.deleteError.set(null);

    this.clientService.deleteConsignment(consignment.consignmentId).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.load();
      },
      error: (err) => {
        this.deletingId.set(null);
        this.deleteError.set(err?.error?.message ?? 'Could not delete consignment.');
      },
    });
  }
}

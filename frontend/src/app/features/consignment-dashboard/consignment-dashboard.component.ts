import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ClientService } from '../../core/services/client.service';
import { ClientSummary, ConsignmentSummary } from '../../core/models/client.model';
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
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);

  readonly consignments = signal<ConsignmentSummary[]>([]);
  readonly clients = signal<ClientSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly showCreateForm = signal(false);
  readonly creating = signal(false);
  readonly createError = signal<string | null>(null);
  readonly deletingId = signal<string | null>(null);
  readonly deleteError = signal<string | null>(null);

  readonly canWrite = this.auth.isOperator && environment.siteId === 'depot';

  readonly createForm = this.fb.nonNullable.group({
    clientId: ['', Validators.required],
    description: ['', Validators.required],
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

    // Best-effort: only needed to render client names / populate the create dropdown.
    // If this fails, consignment rows just fall back to showing the raw clientId.
    this.clientService.listClients().subscribe({
      next: (clients) => this.clients.set(clients),
      error: () => {},
    });
  }

  clientName(clientId: string): string {
    return this.clients().find((c) => c.clientId === clientId)?.name ?? clientId.slice(0, 8);
  }

  submitCreate(): void {
    if (this.createForm.invalid) return;

    this.creating.set(true);
    this.createError.set(null);

    this.clientService.createConsignment(this.createForm.getRawValue()).subscribe({
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

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ClientService } from '../../core/services/client.service';
import { ClientSummary } from '../../core/models/client.model';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

/**
 * Client has no State fragment and no relocate concept, so this
 * dashboard is simpler than the Trip/Container ones: a plain list plus
 * create, no route rail, no status chip. Delete lives here too (as a
 * button per row) rather than on a separate detail page, since there's
 * nothing else worth a whole page for a Client beyond its name.
 */
@Component({
  selector: 'bf-client-dashboard',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './client-dashboard.component.html',
  styleUrls: ['../dashboard/dashboard.component.css', './client-dashboard.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientDashboardComponent implements OnInit {
  private readonly clientService = inject(ClientService);
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);

  readonly clients = signal<ClientSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly showCreateForm = signal(false);
  readonly creating = signal(false);
  readonly createError = signal<string | null>(null);
  readonly deletingId = signal<string | null>(null);
  readonly deleteError = signal<string | null>(null);

  /** Only Depot's backend accepts create/delete -- see OriginSiteOnlyException. */
  readonly canWrite = this.auth.isOperator && environment.siteId === 'depot';

  readonly createForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.clientService.listClients().subscribe({
      next: (clients) => {
        this.clients.set(clients);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not reach this site\'s client list.');
        this.loading.set(false);
      },
    });
  }

  submitCreate(): void {
    if (this.createForm.invalid) return;

    this.creating.set(true);
    this.createError.set(null);

    this.clientService.createClient(this.createForm.getRawValue()).subscribe({
      next: () => {
        this.creating.set(false);
        this.showCreateForm.set(false);
        this.createForm.reset();
        this.load();
      },
      error: (err) => {
        this.creating.set(false);
        this.createError.set(err?.error?.message ?? 'Could not create client.');
      },
    });
  }

  deleteClient(client: ClientSummary): void {
    if (!confirm(`Delete client "${client.name}"? This cannot be undone.`)) return;

    this.deletingId.set(client.clientId);
    this.deleteError.set(null);

    this.clientService.deleteClient(client.clientId).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.load();
      },
      error: (err) => {
        this.deletingId.set(null);
        // A blocked delete (Consignments still reference this Client) is expected, not a system failure.
        this.deleteError.set(err?.error?.message ?? 'Could not delete client.');
      },
    });
  }
}

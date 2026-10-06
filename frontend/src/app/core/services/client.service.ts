import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ClientCreateRequest, ClientSummary, ConsignmentCreateRequest, ConsignmentSummary } from '../models/client.model';

/**
 * Covers both Client and Consignment -- same file as the backend's
 * client/ package holds both ClientController and
 * ConsignmentController together, since they're a tightly coupled
 * pair (every Consignment belongs to exactly one Client) with no
 * State-fragment complexity to justify splitting them.
 */
@Injectable({ providedIn: 'root' })
export class ClientService {
  private readonly http = inject(HttpClient);
  private readonly clientsBase = `${environment.apiBaseUrl}/clients`;
  private readonly consignmentsBase = `${environment.apiBaseUrl}/consignments`;

  listClients(): Observable<ClientSummary[]> {
    return this.http.get<ClientSummary[]>(this.clientsBase);
  }

  getClient(clientId: string): Observable<ClientSummary> {
    return this.http.get<ClientSummary>(`${this.clientsBase}/${clientId}`);
  }

  createClient(request: ClientCreateRequest): Observable<ClientSummary> {
    return this.http.post<ClientSummary>(this.clientsBase, request);
  }

  deleteClient(clientId: string): Observable<void> {
    return this.http.delete<void>(`${this.clientsBase}/${clientId}`);
  }

  listConsignments(): Observable<ConsignmentSummary[]> {
    return this.http.get<ConsignmentSummary[]>(this.consignmentsBase);
  }

  getConsignment(consignmentId: string): Observable<ConsignmentSummary> {
    return this.http.get<ConsignmentSummary>(`${this.consignmentsBase}/${consignmentId}`);
  }

  createConsignment(request: ConsignmentCreateRequest): Observable<ConsignmentSummary> {
    return this.http.post<ConsignmentSummary>(this.consignmentsBase, request);
  }

  deleteConsignment(consignmentId: string): Observable<void> {
    return this.http.delete<void>(`${this.consignmentsBase}/${consignmentId}`);
  }
}

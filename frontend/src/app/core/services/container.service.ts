import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ContainerRelocationRequest, ContainerRelocationResponse, ContainerSummary } from '../models/container.model';

/** Same shape as TripService -- see its class javadoc for why there's no "list every site" call. */
@Injectable({ providedIn: 'root' })
export class ContainerService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/containers`;

  listContainers(): Observable<ContainerSummary[]> {
    return this.http.get<ContainerSummary[]>(this.base);
  }

  getContainer(containerId: string): Observable<ContainerSummary> {
    return this.http.get<ContainerSummary>(`${this.base}/${containerId}`);
  }

  relocate(containerId: string, request: ContainerRelocationRequest): Observable<ContainerRelocationResponse> {
    return this.http.post<ContainerRelocationResponse>(`${this.base}/${containerId}/relocate`, request);
  }
}

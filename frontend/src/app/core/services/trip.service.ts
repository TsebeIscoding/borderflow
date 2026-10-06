import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { HandoverRequest, HandoverResponse, TripCreateRequest, TripSummary } from '../models/trip.model';

/**
 * Talks to THIS site's own backend instance only (environment.apiBaseUrl).
 * There is no "get all trips across the mesh" call, because there's no
 * central server to ask -- every site answers from its own local
 * Postgres, which already has every other site's State-fragment writes
 * via replication. See backend TripController's class javadoc.
 */
@Injectable({ providedIn: 'root' })
export class TripService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/trips`;

  listTrips(): Observable<TripSummary[]> {
    return this.http.get<TripSummary[]>(this.base);
  }

  getTrip(tripId: string): Observable<TripSummary> {
    return this.http.get<TripSummary>(`${this.base}/${tripId}`);
  }

  handOff(tripId: string, request: HandoverRequest): Observable<HandoverResponse> {
    return this.http.post<HandoverResponse>(`${this.base}/${tripId}/handover`, request);
  }

  /** Backend rejects this with 403 unless this site is Depot -- see OriginSiteOnlyException. */
  create(request: TripCreateRequest): Observable<TripSummary> {
    return this.http.post<TripSummary>(this.base, request);
  }

  /** Same origin-only restriction as create(). */
  delete(tripId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${tripId}`);
  }
}

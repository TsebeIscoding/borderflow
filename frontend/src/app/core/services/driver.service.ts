import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DriverCreateRequest, DriverRelocationRequest, DriverRelocationResponse, DriverSummary } from '../models/driver.model';

/** Same shape as VehicleService. */
@Injectable({ providedIn: 'root' })
export class DriverService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/drivers`;

  listDrivers(): Observable<DriverSummary[]> {
    return this.http.get<DriverSummary[]>(this.base);
  }

  getDriver(driverId: string): Observable<DriverSummary> {
    return this.http.get<DriverSummary>(`${this.base}/${driverId}`);
  }

  relocate(driverId: string, request: DriverRelocationRequest): Observable<DriverRelocationResponse> {
    return this.http.post<DriverRelocationResponse>(`${this.base}/${driverId}/relocate`, request);
  }

  create(request: DriverCreateRequest): Observable<DriverSummary> {
    return this.http.post<DriverSummary>(this.base, request);
  }

  delete(driverId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${driverId}`);
  }
}

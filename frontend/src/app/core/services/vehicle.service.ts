import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { VehicleCreateRequest, VehicleRelocationRequest, VehicleRelocationResponse, VehicleSummary } from '../models/vehicle.model';

/** Same shape as ContainerService. */
@Injectable({ providedIn: 'root' })
export class VehicleService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/vehicles`;

  listVehicles(): Observable<VehicleSummary[]> {
    return this.http.get<VehicleSummary[]>(this.base);
  }

  getVehicle(vehicleId: string): Observable<VehicleSummary> {
    return this.http.get<VehicleSummary>(`${this.base}/${vehicleId}`);
  }

  relocate(vehicleId: string, request: VehicleRelocationRequest): Observable<VehicleRelocationResponse> {
    return this.http.post<VehicleRelocationResponse>(`${this.base}/${vehicleId}/relocate`, request);
  }

  create(request: VehicleCreateRequest): Observable<VehicleSummary> {
    return this.http.post<VehicleSummary>(this.base, request);
  }

  delete(vehicleId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${vehicleId}`);
  }
}

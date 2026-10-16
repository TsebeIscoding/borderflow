import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DeliveryPoint } from '../models/delivery-point.model';

/** Read-only for now: delivery points are Master data seeded at Depot and replicated to every site. */
@Injectable({ providedIn: 'root' })
export class DeliveryPointService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/delivery-points`;

  listDeliveryPoints(): Observable<DeliveryPoint[]> {
    return this.http.get<DeliveryPoint[]>(this.base);
  }
}

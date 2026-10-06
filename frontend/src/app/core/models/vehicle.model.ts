/** Mirrors backend VehicleSummaryResponse. Same flat-DTO philosophy as trip.model.ts. */
export interface VehicleSummary {
  vehicleId: string;
  registrationNumber: string;
  capacity: number;
  currentSiteId: string;
  status: string;
  lamportTs: number;
}

export interface VehicleCreateRequest {
  registrationNumber: string;
  capacity: number;
}

export interface VehicleRelocationRequest {
  toSiteId: string;
  verifiedBy: string;
}

export interface VehicleRelocationResponse {
  vehicleId: string;
  fromSiteId: string;
  toSiteId: string;
  lamportTs: number;
}

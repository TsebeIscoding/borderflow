/** Mirrors backend DriverSummaryResponse. */
export interface DriverSummary {
  driverId: string;
  name: string;
  licenseNumber: string;
  phone: string | null;
  currentSiteId: string;
  status: string;
  lamportTs: number;
}

export interface DriverCreateRequest {
  name: string;
  licenseNumber: string;
  phone: string | null;
}

export interface DriverRelocationRequest {
  toSiteId: string;
  verifiedBy: string;
}

export interface DriverRelocationResponse {
  driverId: string;
  fromSiteId: string;
  toSiteId: string;
  lamportTs: number;
}

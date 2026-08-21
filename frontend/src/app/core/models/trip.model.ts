/**
 * Mirrors backend TripSummaryResponse (com.borderflow.trip). Kept as a
 * flat DTO on purpose -- this is a wire type, not a domain model with
 * behavior; any business logic (e.g. "can this trip be handed off?")
 * belongs server-side in HandoverService, not duplicated here.
 */
export interface TripSummary {
  tripId: string;
  originSiteId: string;
  destinationSiteId: string;
  currentSiteId: string;
  status: TripStatus;
  lamportTs: number;
}

export type TripStatus = 'Planned' | 'AtOrigin' | 'Arrived' | 'Delivered';

/** The four physical sites, in corridor order. Drives the route rail. */
export const SITE_ORDER = ['depot', 'border', 'port', 'destination'] as const;
export type SiteId = (typeof SITE_ORDER)[number];

export const SITE_LABELS: Record<string, string> = {
  depot: 'Depot',
  border: 'Border',
  port: 'Port',
  destination: 'Destination',
};

export interface HandoverRequest {
  toSiteId: string;
  verifiedBy: string;
}

export interface HandoverResponse {
  eventId: string;
  tripId: string;
  fromSiteId: string;
  toSiteId: string;
  newStatus: TripStatus;
  lamportTs: number;
}

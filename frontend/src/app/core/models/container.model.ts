/**
 * Mirrors backend ContainerSummaryResponse (com.borderflow.container).
 * Same flat-DTO philosophy as trip.model.ts -- see its comment.
 *
 * No ContainerStatus union like TripStatus -- unlike Trip, Container
 * has no "Delivered" terminal state (container_master has no
 * destination_site_id). Status is always either 'AtOrigin' or
 * 'Arrived' in practice, but kept as a plain string here rather than
 * a union to avoid implying a state machine the backend doesn't
 * actually enforce -- see ContainerRelocationService's class javadoc.
 */
export interface ContainerSummary {
  containerId: string;
  containerNumber: string;
  consignmentId: string;
  size: string;
  currentSiteId: string;
  status: string;
  lamportTs: number;
}

export interface ContainerRelocationRequest {
  toSiteId: string;
  verifiedBy: string;
}

export interface ContainerRelocationResponse {
  containerId: string;
  fromSiteId: string;
  toSiteId: string;
  newStatus: string;
  lamportTs: number;
}

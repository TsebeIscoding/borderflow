/**
 * Mirrors backend ClientSummaryResponse -- name only, deliberately.
 * client_contact (PII) has no entity, endpoint, or code path anywhere
 * in the backend, so there is nothing for this model to represent
 * even if it wanted to -- see backend ClientCore's class javadoc.
 */
export interface ClientSummary {
  clientId: string;
  name: string;
}

export interface ClientCreateRequest {
  name: string;
}

/** Mirrors backend ConsignmentSummaryResponse. No State fragment, no movement concept -- static Master data. */
export interface ConsignmentSummary {
  consignmentId: string;
  clientId: string;
  description: string;
}

export interface ConsignmentCreateRequest {
  clientId: string;
  description: string;
}

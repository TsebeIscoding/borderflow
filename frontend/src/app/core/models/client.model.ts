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
  /** Null only for consignments created before delivery points existed and not yet backfilled. */
  deliveryPointId: string | null;
  /** Issued by the backend, e.g. CON-2026-0001. The detail fields below are null on consignments created before they existed. */
  referenceNumber: string | null;
  cargoType: string | null;
  totalWeightKg: number | null;
  packageCount: number | null;
  declaredValue: number | null;
  currency: string | null;
  customsReference: string | null;
  handlingNotes: string | null;
  /** A business address as free text -- never a person's address. */
  deliveryLocation: string | null;
  /** ISO date, yyyy-MM-dd. */
  expectedDeliveryDate: string | null;
}

export const CARGO_TYPES = ['General', 'Refrigerated', 'Hazardous', 'Bulk'] as const;
export const CURRENCIES = ['LSL', 'ZAR', 'USD'] as const;

/** The reference number is never sent -- the backend issues it. Declared value and currency go together. */
export interface ConsignmentCreateRequest {
  clientId: string;
  description: string;
  deliveryPointId: string;
  cargoType: string;
  totalWeightKg: number;
  packageCount: number;
  declaredValue: number | null;
  currency: string | null;
  customsReference: string | null;
  handlingNotes: string | null;
  deliveryLocation: string | null;
  expectedDeliveryDate: string | null;
}

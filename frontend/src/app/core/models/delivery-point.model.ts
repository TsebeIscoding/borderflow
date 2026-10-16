/**
 * Mirrors backend DeliveryPointSummaryResponse. A delivery point is a town
 * inside the Destination site's area (Maseru, Butha-Buthe, ...), not a
 * separate site -- the corridor stays Depot, Border, Port, Destination.
 */
export interface DeliveryPoint {
  deliveryPointId: string;
  name: string;
}

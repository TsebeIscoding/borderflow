import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { SITE_LABELS, SITE_ORDER, SiteId } from '../../core/models/trip.model';

/**
 * The signature visual element, used both small (dashboard rows) and
 * large (trip detail). Modeled on a customs manifest's routing stamp:
 * a straight line of waypoints, each one either cleared (checked,
 * green), current (lit, orange), or ahead (dim). Deliberately not a
 * generic progress bar -- the waypoints are named, physical places, and
 * that specificity is the point.
 */
@Component({
  selector: 'bf-route-rail',
  standalone: true,
  templateUrl: './route-rail.component.html',
  styleUrl: './route-rail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RouteRailComponent {
  @Input({ required: true }) currentSiteId!: string;
  @Input() delivered = false;
  @Input() compact = false;

  readonly sites = SITE_ORDER;
  readonly labels = SITE_LABELS;

  waypointState(site: SiteId): 'cleared' | 'current' | 'ahead' {
    const currentIndex = this.sites.indexOf(this.currentSiteId as SiteId);
    const siteIndex = this.sites.indexOf(site);
    if (this.delivered) return 'cleared';
    if (siteIndex < currentIndex) return 'cleared';
    if (siteIndex === currentIndex) return 'current';
    return 'ahead';
  }
}

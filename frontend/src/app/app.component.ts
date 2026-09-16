import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { environment } from '../environments/environment';
import { AuthService } from './core/auth/auth.service';

@Component({
  selector: 'bf-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  readonly siteLabel = environment.siteLabel;
  readonly auth = inject(AuthService);
}

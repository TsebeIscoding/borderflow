import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Redirects to /login if there's no session. Doesn't check role here
 * -- role-specific UI (e.g. hiding the handover form for an AUDITOR)
 * is handled in the components themselves, since it's about what to
 * show, not whether the route is reachable at all. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated) {
    return true;
  }

  return router.parseUrl('/login');
};

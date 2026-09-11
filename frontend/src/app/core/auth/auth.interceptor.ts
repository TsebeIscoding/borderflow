import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Attaches the stored token to every outgoing request except the login
 * call itself (which obviously can't have a token yet). Every other
 * request either gets a valid Authorization header or none at all --
 * there's no partial/refresh-token dance here, matching the backend's
 * equally simple validate-or-reject model (see JwtService.validate).
 *
 * A 401 anywhere (expired token, or one that stopped validating for
 * any other reason) forces a logout instead of leaving the user stuck
 * looking at a broken screen with a token that will never start
 * working again on its own.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isLoginCall = req.url.includes('/auth/login');

  const request = isLoginCall || !auth.token
    ? req
    : req.clone({ setHeaders: { Authorization: `Bearer ${auth.token}` } });

  return next(request).pipe(
    catchError((err) => {
      if (!isLoginCall && err?.status === 401) {
        auth.logout();
      }
      return throwError(() => err);
    })
  );
};

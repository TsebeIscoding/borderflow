import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, UserRole } from './auth.models';

const STORAGE_KEY = 'borderflow.auth';

interface StoredSession {
  token: string;
  username: string;
  role: UserRole;
}

/**
 * Owns the token this browser tab is currently holding. The token
 * itself is opaque to the frontend -- all we do with it is attach it
 * to outgoing requests (see auth.interceptor.ts) and read its `role`
 * back out of the LOGIN RESPONSE (never by decoding the JWT client-side;
 * the backend is the only thing that ever validates it, this is purely
 * for showing/hiding UI).
 *
 * Storing the token in localStorage is a normal choice for a real
 * deployed app like this one -- unlike a claude.ai artifact sandbox,
 * this runs as an actual browser app the person installs/serves
 * themselves, so standard browser storage APIs are fully available and
 * expected here.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly session = signal<StoredSession | null>(this.readStoredSession());

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiBaseUrl}/auth/login`, request).pipe(
      tap((response) => {
        const session: StoredSession = { token: response.token, username: response.username, role: response.role };
        localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
        this.session.set(session);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
    this.router.navigateByUrl('/login');
  }

  get token(): string | null {
    return this.session()?.token ?? null;
  }

  get isAuthenticated(): boolean {
    return this.session() !== null;
  }

  get isOperator(): boolean {
    return this.session()?.role === 'OPERATOR';
  }

  private readStoredSession(): StoredSession | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as StoredSession;
    } catch {
      return null;
    }
  }
}

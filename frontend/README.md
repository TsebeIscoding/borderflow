# BorderFlow frontend

Angular UI for site staff: view the local manifest (every trip this
site's database currently knows about) and hand off custody of a trip
this site currently holds. One build of this frontend is deployed per
site, each pointed at that site's own backend instance — same principle
as the backend itself (see `../backend/README.md`): there is no shared
"central" frontend, because there's no central API to point it at.

## Why there's no "view trips at every site" screen

Deliberately absent. Each site's frontend only ever talks to that
site's own backend (`/api`, proxied to `localhost:8080` in dev — see
`proxy.conf.json`), which only ever reads from that site's own local
Postgres. That database already has every other site's State-fragment
writes via logical replication (see `../db/migrations`), so the
manifest is complete without a single cross-site network call. Building
a "global" view would mean picking one site to be a dependency for
everyone else's dashboard — exactly the single point of failure the
whole multi-leader design exists to avoid.

## Structure

```
src/app/
├── core/
│   ├── auth/                       AuthService (session + token storage),
│   │                               HTTP interceptor, route guard
│   ├── models/trip.model.ts        Wire types matching backend DTOs,
│   │                               plus SITE_ORDER/SITE_LABELS driving
│   │                               the route rail
│   └── services/trip.service.ts    All HTTP calls, nothing else
├── shared/
│   └── route-rail/                 The one signature visual element,
│                                   reused compact (dashboard rows) and
│                                   full-size (trip detail)
└── features/
    ├── login/                       Sign-in form, posts to /api/auth/login
    ├── dashboard/                   Manifest table — every trip, status,
    │                               compact route rail, links to detail
    └── trip-detail/                Full route rail + the handover form
```

## Authentication

Every route except `/login` is behind `authGuard`, which redirects to
the login form if there's no stored session. `AuthService` keeps the
token in `localStorage` (a normal, expected choice for a real deployed
app like this one — see the class javadoc on `AuthService` for why
that's different from a claude.ai artifact sandbox, where browser
storage is off-limits) and an HTTP interceptor
(`auth.interceptor.ts`) attaches it to every outgoing request except
the login call itself. A `401` response anywhere forces a logout
rather than leaving the user stuck on a screen with a token that will
never start working again on its own.

The frontend never decodes the JWT itself — the `role` shown in the
header and used to hide/show the handover form comes straight from
`POST /api/auth/login`'s response body, not from inspecting the
token. The backend is the only thing that ever actually validates a
token; see `../backend/README.md`'s Authentication and authorization
section for the full OPERATOR/AUDITOR trust model.

## The handover form's rules aren't just UI polish

`TripDetailComponent.canInitiateHandover()` hides the form unless the
signed-in user is an OPERATOR, this site currently holds the trip, and
it isn't already `Delivered` — the same rules `HandoverController` and
`HandoverService` enforce server-side (see `../backend/README.md`).
This is presentation-layer convenience, not a security boundary:
hiding the form doesn't stop a direct API call, the backend re-checks
everything regardless (`@PreAuthorize` plus the same site/status
checks). If the two ever disagree, the backend wins and the UI just
shows whatever error message comes back.

## Local development

Requires the corresponding backend instance running on `localhost:8080`
(`../backend`) and its `SITE_ID` matching `environment.ts`'s `siteId`
here — otherwise `canInitiateHandover()` will never be true for any
trip, since it compares against a mismatched site.

```bash
npm install
npm start          # ng serve, proxies /api to localhost:8080
```

You'll land on `/login` first — sign in with one of the demo accounts
listed in `../backend/README.md` (`operator1` / `ChangeMe123!` works
at every site).

`environment.ts` is intentionally checked in with real per-site values
(`siteId`, `siteLabel`) rather than left as a template — swap them
per site before building that site's bundle, or wire an actual
per-environment build pipeline later if this grows past four sites.

## Not yet built

- No view for Container/Vehicle/Driver/Client fragments — the backend
  only exposes Trip endpoints so far (see root `README.md`'s status
  checklist for what's pending on the schema side).
- No token refresh — a session simply expires (`expiration-minutes` in
  the backend's `application.yml`, 60 by default) and the next request
  gets a 401, which forces a re-login. Fine for a portfolio project,
  a real deployment would want a refresh flow so an active user isn't
  interrupted mid-task.

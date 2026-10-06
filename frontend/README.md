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
│   ├── auth/                        AuthService (session + token storage),
│   │                                HTTP interceptor, route guard
│   ├── models/                      Wire types matching backend DTOs
│   │   ├── trip.model.ts            Also holds SITE_ORDER/SITE_LABELS,
│   │   │                            shared across every feature
│   │   ├── container.model.ts
│   │   ├── vehicle.model.ts
│   │   ├── driver.model.ts
│   │   └── client.model.ts           Holds BOTH Client and Consignment --
│   │                                see client.service.ts's comment for why
│   └── services/                    All HTTP calls, nothing else
│       ├── trip.service.ts
│       ├── container.service.ts
│       ├── vehicle.service.ts
│       ├── driver.service.ts
│       └── client.service.ts
├── shared/
│   └── route-rail/                  The one signature visual element,
│                                    reused compact (dashboard rows) and
│                                    full-size (detail pages), across
│                                    every entity with a State fragment
└── features/
    ├── login/                       Sign-in form, posts to /api/auth/login
    ├── dashboard/                   Trip manifest + create form
    ├── trip-detail/                 Route rail + handover form + delete
    ├── container-dashboard/          Container manifest + create form
    ├── container-detail/             Route rail + relocate form + delete
    ├── vehicle-dashboard/            Same shape as container-dashboard/
    ├── vehicle-detail/               Same shape as container-detail/
    ├── driver-dashboard/             Same shape as vehicle-dashboard/
    ├── driver-detail/                Same shape as vehicle-detail/
    ├── client-dashboard/             List + create + delete, no detail
    │                                page and no route rail -- Client has
    │                                no State fragment, nothing to relocate
    └── consignment-dashboard/        Same shape as client-dashboard/,
                                     its create form's Client dropdown is
                                     populated via client.service.ts
```

All six entities are reachable via the nav links in the header
(`app.component.html`), shown once signed in.

## CRUD coverage, and why it isn't uniform across entities

Every entity has **Create**, **Read**, and **Delete** in the UI. What
each entity does **not** have is a generic "Update" form — because the
backend doesn't have one either, on purpose (see
`../backend/README.md`'s CRUD section). What exists instead:

- **Trip** and **Container** — the handover/relocate form on their
  detail pages *is* their Update operation, scoped to the State
  fragment (the only part meant to change after creation)
- **Vehicle** and **Driver** — same pattern, their own relocate form
- **Client** and **Consignment** — no Update at all, because they have
  no State fragment and nothing about them is meant to change once
  created. They also have no detail page — a name, or a client +
  description, doesn't need a whole page; list + inline delete is
  enough.

**Create and Delete buttons only render when `environment.siteId ===
'depot'`** (see each dashboard's `canCreate`/`canWrite`), because the
backend only ever accepts these at the origin site
(`OriginSiteOnlyException`). Hiding the button elsewhere is purely
UX — the backend re-enforces the same rule regardless, so a direct API
call from a non-Depot site still gets rejected even if a client
somehow bypassed the UI.

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

**One site:**
```bash
npm install
npm start          # ng serve, Depot config, proxies /api to localhost:8080
```

**All four sites at once**, each in its own terminal (requires all
four backend instances already running — see
`../backend/README.md`'s "Running all four sites at once"):
```bash
npm run start:depot          # localhost:4200, proxies to backend :8080
npm run start:border         # localhost:4201, proxies to backend :8081
npm run start:port           # localhost:4202, proxies to backend :8082
npm run start:destination    # localhost:4203, proxies to backend :8083
```

Each of these is a real Angular build configuration (`angular.json`),
not a manual file swap — `start:border` builds with
`environment.border.ts` in place of `environment.ts` via Angular's own
`fileReplacements` mechanism, proxies through `proxy.conf.border.json`
to Border's backend port, and serves on its own port so all four can
run side by side on one machine without colliding. `npm start` /
`start:depot` are equivalent — Depot is the default, matching
`environment.ts`'s own values.

You'll land on `/login` first on each — sign in with one of the demo
accounts listed in `../backend/README.md` (`operator1` /
`ChangeMe123!` works at every site). With all four running, this is
where you can actually watch a trip move — hand it off from Depot's
tab, then refresh Border's tab and see it arrive, propagated purely
through Postgres logical replication, not through anything the
frontend or backend did to "sync" the two sites.

## Not yet built

- No token refresh — a session simply expires (`expiration-minutes` in
  the backend's `application.yml`, 60 by default) and the next request
  gets a 401, which forces a re-login. Fine for a portfolio project,
  a real deployment would want a refresh flow so an active user isn't
  interrupted mid-task.
- No Trip-Container linkage UI, no Milestone/Incident timeline UI —
  the backend now has full support for all three
  (`POST /api/trips/{tripId}/containers/{containerId}` to link,
  `POST /api/trips/{tripId}/milestones`,
  `POST /api/trips/{tripId}/incidents` — see `backend/README.md`), but
  nothing in this Angular app calls any of them yet. Until this is
  built, a Trip and a Container you create are tracked as separate,
  unconnected records in the UI even though the API to connect them
  already exists.

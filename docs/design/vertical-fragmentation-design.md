# BorderFlow — Distributed Database Redesign (Vertical-Fragmentation-First)

## 0. Why vertical fragmentation actually fits this system

Look at almost every entity in the 3NF schema and it splits cleanly into two kinds of columns:

- **Descriptive / master attributes** — set once, rarely change, needed everywhere for validation and display (e.g. `container_number`, `origin_site_id`, `registration_number`).
- **State / custody attributes** — change constantly, are only meaningful "here and now", and are only ever written by whichever site currently has physical or operational custody of the thing (e.g. `status`, `current_site_id`).

That is exactly the textbook justification for **vertical fragmentation**: split a relation into column subsets, store them where they are used/written, and rejoin on the shared primary key when a global view is needed (the Control Tower does this).

Horizontal fragmentation is *not* abandoned — it is used only where it is structurally unavoidable (append-only event logs, which are generated locally and never edited by anyone else). That satisfies "both if necessary" while keeping vertical as the dominant strategy, which is what your lecturer wants to see argued.

### 0.1 Two separate decisions — don't conflate them in the write-up

**Fragmentation** and **replication** answer different questions, and this design makes an explicit, independent choice for each:

| Question | Answer in this design |
|---|---|
| **Fragmentation** — how is each relation split up? | **Vertical** (by column: Master vs. State) as the primary strategy; **horizontal** (by originating site) only for the append-only event logs, where there is nothing to vertically split |
| **Replication** — once split, how are copies of each fragment kept in sync across sites? | **Native PostgreSQL logical replication** as the mechanism (§9); **single-leader** for Master fragments (one owning site, everyone else read-only), **multi-leader with per-row leader migration** for State fragments (§Cross-site replication discussion) |

A fragment can be replicated with any strategy regardless of whether it was split vertically or horizontally — the two choices are independent. If asked "why vertical fragmentation," the answer is about column ownership matching real access patterns and security boundaries (§7). If asked "why multi-leader replication," the answer is about offline-first correctness under partition (§5–§6). Keep these as two separate justifications, not one combined argument — that's usually where marks are lost.

---

## 1. Site / Node Architecture

| Node | Role | Type |
|---|---|---|
| **Depot/Yard** | Consignment intake, gate-in/out, seal checks | Full operational site |
| **Border Post** | Clearance, handover to/from border | Full operational site |
| **Port Agent** | Port milestones, container handling | Full operational site |
| **Destination Hub** | Final delivery, gate-in, release | Full operational site |
| **Control Tower** | Global read view, KPIs, reconciliation | Aggregator / reporting node (eventually consistent) |

Each of the four operational sites runs its own local database (own Postgres instance in its own Kubernetes namespace/pod) and must be able to accept writes with **zero connectivity** to the others. Control Tower is not a fifth operational site — it's the union of all fragments, replicated in, used for the "control tower view" requirement and for conflict-resolution/audit visibility.

---

## 2. Vertical Fragmentation Scheme

For each fragmented table: `F1` = static/master fragment, `F2` = volatile/state fragment. Both share the same primary key so they can be rejoined.

### 2.1 Container
```
Container_Master(container_id PK, container_number UNIQUE, consignment_id FK, size)
   → written once at Depot (intake), fully REPLICATED to all sites (read-only elsewhere)

Container_State(container_id PK/FK, status, current_site_id, last_milestone_id, updated_at, updated_by_site)
   → written ONLY by the site that currently owns the container (see §4 ownership token)
   → propagated async to other sites as a replica-of-record, not co-written
```

### 2.2 Trip
```
Trip_Master(trip_id PK, origin_site_id FK, destination_site_id FK, created_at)
   → written once at origin site, REPLICATED to all sites touched by the trip

Trip_State(trip_id PK/FK, status, current_site_id, updated_at)
   → single-writer: only the site currently "holding" the trip may update it
```

### 2.3 Vehicle / Driver
```
Vehicle_Profile(vehicle_id PK, registration_number UNIQUE, capacity)
Driver_Profile(driver_id PK, name, license_number UNIQUE, phone)
   → static, mastered at Depot (home base of the fleet), REPLICATED everywhere

Vehicle_Availability(vehicle_id PK/FK, status, current_site_id, updated_at)
Driver_Availability(driver_id PK/FK, status, current_site_id, updated_at)
   → written by whichever site the vehicle/driver is currently interacting with
```

### 2.4 Client / Consignment
```
Client_Core(client_id PK, name)
Client_Contact(client_id PK/FK, contact_info)
   → deliberately split for a security reason, see §7 — Client_Contact is only
     replicated to Depot and Control Tower, NOT pushed to Border Post/Port/Destination,
     since operational sites never need it and it is the one PII-bearing column in
     the whole schema.

Consignment(consignment_id PK, client_id FK, description)
   → static, REPLICATED everywhere (small table, low churn)
```

### 2.5 Assignment
`Assignment` lives with `Trip_State` — it is created and updated at whichever site currently owns the trip, since assignment validity is gated by trip status (business rule 5.v). No separate fragmentation needed; it travels with the trip's state fragment.

---

## 3. Where Horizontal Fragmentation Is Still Necessary

`Handover`, `Milestone`, `Incident`, and `Trip_Container` are **append-only event logs**. There is nothing to "vertically split" in an append-only row — every column is written together, once, by the site where the event physically happened. These are naturally **horizontally partitioned by the site that originated them**:

| Table | Partitioned by | Owning site rule |
|---|---|---|
| `Handover` | `from_site_id` | Site that initiates the handover writes it |
| `Milestone` | `site_id` | Site where the milestone occurred writes it |
| `Incident` | trip's current site | Site handling the trip when the incident occurs |
| `Trip_Container` | trip's origin site | Set once at trip creation, replicated onward |

Because these are insert-only (never updated in place), they behave like CRDT grow-only sets: merging two sites' logs is just a union, so there is no real "conflict" to resolve — only duplicate-delivery to guard against (§5).

This is the honest answer to "why not fragment these vertically too": there is only one meaningful column-group per row here, and the row is atomic to the event — vertical splitting would just add join overhead for no autonomy benefit.

---

## 4. Ownership-Token Pattern (avoids most conflicts before they happen)

Rather than letting every site write to `Trip_State`/`Container_State` concurrently (which invites classic multi-master conflicts), custody is modeled as a **single-writer token**:

1. Only the site named in `current_site_id` may update the corresponding `*_State` fragment.
2. Custody transfers **only** via a `Handover` event — writing the Handover row and flipping `current_site_id` happen in one local transaction at the receiving site.
3. Because Handover events are strictly ordered per trip (a trip can only be handed over sequentially, never to two places at once under normal operation), state-fragment writes are naturally serialized.

Edge case: a network partition could let two sites both believe they hold custody (e.g. a Handover was recorded locally but not yet synced when a second, conflicting Handover is recorded elsewhere). This is the one real conflict class in the system, and it's resolved as below.

---

## 5. Synchronization Protocol

- **Pattern:** transactional outbox at each site. Every local write that must propagate also inserts a row into a local `outbox` table in the same transaction. A background sync agent (sidecar pod) ships outbox rows to peer sites and to Control Tower whenever connectivity exists, then marks them sent.
- **Idempotency:** every event (`Handover`, `Milestone`, `Incident`) carries a client-generated UUID `event_id` assigned at creation time, offline. Receivers `INSERT ... ON CONFLICT (event_id) DO NOTHING` — replays from retried sync are harmless.
- **Ordering:** each event also carries a Lamport-style logical clock `(site_id, local_seq)`. Consumers apply events in causal order per trip.
- **Conflict resolution:**
  | Fragment type | Conflict policy |
  |---|---|
  | Master/replicated (`Container_Master`, `Trip_Master`, profiles, `Consignment`) | Single owner writes, others are read-only replicas → no conflicts possible |
  | State fragments (`Trip_State`, `Container_State`, availability) | Ownership-token first; if two conflicting Handovers are detected on reconciliation, **last-writer-wins by Lamport clock, tie-broken by site priority (origin site wins)**, and the conflict is flagged as an exception record surfaced to Control Tower for manual review — never silently dropped, to satisfy the auditability requirement |
  | Event logs (`Handover`, `Milestone`, `Incident`, `Trip_Container`) | Union/merge, deduped by `event_id` — effectively conflict-free |

---

## 6. Cross-Site Workflow with Correctness Argument

**Scenario: container crosses the border while Border Post is disconnected from the rest of the network.**

1. Depot creates `Trip_Master` + `Assignment` (needs no other site — fully local).
2. Truck departs; Depot writes `Milestone("In transit")` locally.
3. Border Post loses connectivity to Depot/Port/Destination/Control Tower for 30 minutes.
4. While disconnected, Border Post still:
   - receives the truck and records `Handover(Depot → Border)` locally, flipping `Container_State.current_site_id` to itself in the same local transaction (custody token acquired locally — valid because Border Post is the only site that could plausibly claim custody here),
   - records clearance `Milestone`s (`Arrived → Queued → Cleared → Gate in`) locally, validated against the *locally replicated* last-known milestone (state machine check enforced client-side against its own `Container_State` replica),
   - records an `Incident` if a document problem arises — linked to trip/container, fully local write.
5. Connectivity is restored. The outbox on Border Post flushes: `Handover`, `Milestone`s, and the `Container_State` update propagate to all other sites and Control Tower, applied idempotently and in Lamport order.
6. **Correctness argument:** No update is lost, because every local write is durable before sync (outbox is transactional, not fire-and-forget). No update is duplicated, because `event_id` makes every replay a no-op. No two sites can simultaneously and validly hold the ownership token under normal partition (only isolated) failure, because custody transfer is gated by a single physical handover event that can only be recorded at the receiving site. The system therefore achieves **eventual consistency with a bounded, auditable exception path** for the one conflict class that *can* occur (genuinely concurrent/erroneous double-handover), rather than silently choosing a winner.
7. **Demo-ready failure/recovery:** kill network policy to the Border Post pod for the 30-minute window, show local writes succeeding throughout, restore the network policy, show the outbox drain and Control Tower converge to the correct final state.

---

## 7. Trade-offs: Vertical (chosen) vs Horizontal (rejected as primary)

| | Vertical fragmentation (this design) | Pure horizontal (fragment whole rows by region) |
|---|---|---|
| Bandwidth over weak border links | Low — only small, high-churn state columns move per event; bulky/static columns replicate rarely | High — full rows (including rarely-changing columns) re-sent whenever any column changes |
| Fit to real access patterns | Good — Border Post only ever touches state/clearance columns, never client billing/contact data | Poor — every site holds full rows even for columns it never reads |
| Security / data minimization | Strong — `Client_Contact` (the one PII column) is excluded from operational-site replicas entirely | Weak — full client row (incl. PII) would need to sit on every regional shard |
| Query simplicity | Slightly worse — global view requires joining `Master` + `State` fragments (mitigated: Control Tower does this once, centrally) | Better — full row available directly on the owning shard |
| Autonomy under partition | Good — each site can read+write its own state fragment without needing the master fragment for anything but validation-at-creation | Good, but for the wrong reason — autonomy comes from row ownership, not from matching real write patterns |

Net: vertical fragmentation is chosen as the primary strategy because it maps directly onto how BorderFlow actually behaves (one slow-changing description, one fast-changing custody state, different sites caring about different columns), and it directly strengthens the Security and Observability non-functional requirements. Horizontal partitioning is retained only for the append-only event logs, where it's the only sensible option.

---

## 8. Implementation Notes for the Kubernetes Prototype

- Each operational site = its own namespace: `depot`, `border`, `port`, `destination`, each with a local Postgres StatefulSet + a `sync-agent` sidecar deployment reading/writing the `outbox` table.
- `control-tower` namespace runs a reporting Postgres (or materialized view service) that only ever receives inbound replication — it never originates writes to operational fragments.
- Simulate the "at least four independent sites" and "offline-first" requirements with Kubernetes `NetworkPolicy` objects you can toggle to cut a site off from the others for the failure/recovery demo.
- Recommend a lightweight message-relay (or simple authenticated REST polling if you want to avoid standing up Kafka) between sync-agents, since the mandatory requirement is the idempotency/conflict-handling logic, not the transport.

> **Revision note:** §9 below supersedes the "outbox table + custom sync agent" mechanism described above. The lecturer specifically wants replication implemented *inside* the DDBMS (Postgres's own logical replication engine), not as an application-tier pattern. The fragment design, ownership-token model, and conflict-resolution *policy* (§4–§6) are unchanged — only *how* changes move between sites changes.

---

## 9. Full System Architecture & Tech Stack (Replication Inside the DDBMS)

### 9.1 Why this replaces the outbox pattern

The earlier design used an application-level outbox table + a custom sync agent shipping events between sites — that's replication built *around* the database, in the app tier. Your lecturer wants replication built *into* the database itself. PostgreSQL's native logical replication does exactly that: it reads the Write-Ahead Log (WAL) directly and streams row changes to subscribers over a replication protocol connection — no outbox table, no custom agent process, no message queue. The "shipping mechanism" becomes a first-class DBMS feature (`CREATE PUBLICATION` / `CREATE SUBSCRIPTION`) instead of code you write and maintain.

### 9.2 Core DBMS: PostgreSQL 18

Every site (Depot, Border, Port, Destination) and Control Tower runs its own **PostgreSQL 18** instance. PG18 is the current stable release; logical replication and column/row-filtered publications are core features (no extension required for the baseline design).

### 9.3 Mapping fragments onto native replication features

**Master fragments → one-way publication, with column filtering doing the vertical split for you**
```sql
-- On Depot (owner of Client/Consignment/Container_Master/Trip_Master/profiles)
CREATE PUBLICATION pub_master_operational
  FOR TABLE client_core, consignment,
             container_master, trip_master,
             vehicle_profile, driver_profile;
-- Client_Contact is a SEPARATE table, deliberately left OUT of this publication
-- so operational sites never receive it at all — enforced by Postgres itself,
-- not by application logic.

-- On Border / Port / Destination:
CREATE SUBSCRIPTION sub_master_from_depot
  CONNECTION 'host=depot-postgres dbname=borderflow ...'
  PUBLICATION pub_master_operational;
```
`Client_Contact` gets its own publication that only Control Tower (and Depot itself) subscribes to. This is the fragment-level security boundary from §7, implemented as a genuine DDBMS access control, not app code deciding what to forward. (Postgres 15+ also supports row-filtered publications with a `WHERE` clause — useful later if you want, e.g., Control Tower to receive everything but an operational site to receive only rows relevant to it.)

**State fragments → mesh bidirectional publications (multi-leader, as designed in the replication-strategy discussion)**
```sql
-- On EACH of Depot/Border/Port/Destination:
CREATE PUBLICATION pub_state_local
  FOR TABLE trip_state, container_state, vehicle_availability, driver_availability;

-- Each site subscribes to the other three:
CREATE SUBSCRIPTION sub_state_from_border
  CONNECTION 'host=border-postgres dbname=borderflow ...'
  PUBLICATION pub_state_local
  WITH (origin = NONE);   -- prevents replication loops in a mesh (PG16+)
```
`origin = NONE` is what makes a true multi-leader mesh safe in native Postgres — without it, a change replicated from Border to Port could replicate right back to Border, and so on. This is the built-in mechanism behind the "leadership migrates per row" model from before.

**Event logs → mesh bidirectional, made idempotent with a trigger instead of an outbox**

Native logical replication will **error and halt the subscription** if a replicated `INSERT` collides with an existing primary key — it does not silently deduplicate. Since our `event_id` is exactly the idempotency key we rely on, we need a small trigger so a duplicate delivery becomes a no-op instead of breaking replication:
```sql
CREATE OR REPLACE FUNCTION skip_duplicate_event() RETURNS trigger AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM handover WHERE event_id = NEW.event_id) THEN
    RETURN NULL;  -- silently absorb the duplicate
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dedupe_handover
  BEFORE INSERT ON handover
  FOR EACH ROW EXECUTE FUNCTION skip_duplicate_event();

-- CRITICAL: the logical replication apply process runs with
-- session_replication_role = 'replica', so a normal trigger only fires on
-- LOCAL writes and is silently skipped for rows arriving via replication.
-- Without the line below, this dedupe logic would never actually run when
-- it matters (on replicated inserts) — only on local ones.
ALTER TABLE handover ENABLE ALWAYS TRIGGER trg_dedupe_handover;

-- ANOTHER CRITICAL ONE: since Postgres 13 (security fix, similar to
-- CVE-2018-1058), the logical replication apply worker runs with search_path
-- deliberately set to EMPTY. Any trigger function that references a table by
-- its plain name (e.g. "handover" instead of "public.handover") will fail
-- with "relation does not exist" — but ONLY when fired by replication, never
-- on a normal local write, which makes this easy to miss until you test the
-- exact replication path. Fix: pin the function's search_path explicitly.
ALTER FUNCTION skip_duplicate_handover() SET search_path = public;
```
Same pattern for `milestone` and `incident`. This trigger runs *inside* Postgres — it's still DDBMS-level, just not automatic the way pure CRDT merge would be.

### 9.4 Conflict detection vs. resolution — what's native, what you still build

- **Detection is native as of PG17/18**: conflicts (`insert_exists`, `update_origin_differs`, `delete_origin_differs`, etc.) are automatically logged and counted in `pg_stat_subscription_stats` — this is a genuine DDBMS feature you can point to directly in your report for the Auditability requirement.
- **Resolution is not automatic in core Postgres.** For the rare double-custody case from §5, add one more trigger implementing the Lamport-clock/site-priority LWW rule:
```sql
CREATE OR REPLACE FUNCTION resolve_state_conflict() RETURNS trigger AS $$
BEGIN
  IF NEW.lamport_ts < OLD.lamport_ts
     OR (NEW.lamport_ts = OLD.lamport_ts AND NEW.site_id > OLD.site_id) THEN
    INSERT INTO conflict_exceptions(table_name, row_key, local_row, incoming_row, detected_at)
      VALUES (TG_TABLE_NAME, OLD.trip_id, row_to_json(OLD), row_to_json(NEW), now());
    RETURN NULL;  -- reject the losing update, keep local state, flag for review
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_resolve_trip_state_conflict
  BEFORE UPDATE ON trip_state
  FOR EACH ROW EXECUTE FUNCTION resolve_state_conflict();

-- Same reason as above: this MUST be ENABLE ALWAYS or it never fires on
-- rows arriving from another site, which is the entire point of it.
ALTER TABLE trip_state ENABLE ALWAYS TRIGGER trg_resolve_trip_state_conflict;

-- Same empty-search_path issue as above — required or this function fails
-- the exact same way the moment a conflicting update arrives via replication.
ALTER FUNCTION resolve_trip_state_conflict() SET search_path = public;
```
This gives you exactly the policy from §5 (origin-site-wins tiebreak, exceptions surfaced for manual review) — but implemented as a Postgres trigger function, satisfying "replication inside the DDBMS" while staying fully explainable and debuggable within a 6-week project timeline.

- **Stretch option if time allows:** swap the hand-rolled triggers for **pgEdge Spock** (open-source multi-master extension, actively maintained) or **BDR**, which provide automatic conflict resolution (e.g. delta-apply, configurable LWW) out of the box. This costs you extra install/ops complexity in Kubernetes and less transparency into *why* a conflict resolved the way it did — worth mentioning in your report as a considered alternative, with the trigger-based approach chosen for its explainability and lower setup risk within the timeline.

- **One real limitation to state up front in your report:** native logical replication does **not** replicate DDL (schema changes). Every site's schema must be provisioned identically via a shared migration tool (e.g. Flyway or a simple versioned `.sql` init script applied at every site's container startup) — never by relying on replication to propagate a `CREATE TABLE`/`ALTER TABLE`.

### 9.5 Full Stack Summary

| Layer | Choice | Notes |
|---|---|---|
| DDBMS + replication | **PostgreSQL 18**, native logical replication (column/row-filtered publications, mesh subscriptions, `origin=NONE`) | Replaces outbox/sync-agent entirely |
| Conflict policy | PL/pgSQL trigger functions (dedupe + LWW) | Inside the DDBMS, not app code |
| API/service layer | **Java + Spring Boot, Spring Data JPA** — REST per site | Talks ONLY to its own local Postgres; never calls another site's API directly — sites are decoupled, replication is DB-to-DB |
| Build tool | **Maven** | |
| Frontend | Minimal React (or plain HTML/JS) per site role + a separate Control Tower dashboard | Matches "UI must be minimal but functional" |
| Orchestration | Kubernetes: one namespace per site + `control-tower` namespace | Postgres as a StatefulSet + PVC, Spring Boot API as a Deployment, per namespace |
| Failure simulation | Kubernetes `NetworkPolicy` toggled to isolate a namespace | Drives the mandatory failure/recovery demo |
| Observability | Prometheus + Grafana, scraping `pg_stat_subscription_stats` / `pg_stat_replication` via `postgres_exporter`, **plus Spring Boot Actuator + Micrometer for application-level metrics** | DB-level replication health and app-level API health both feed the same Grafana dashboards |
| Security | TLS on replication connections, least-privilege DB roles, **Spring Security (mandatory — see §10)**, Kubernetes Secrets for credentials, optional Row-Level Security as a second guard on `client_contact` | |
| Schema migrations | **Flyway** | Natural fit with Spring Boot; see §9.6 — this is also how "DDL is never replicated" gets solved cleanly |

### 9.6 Spring Boot integration notes — read before writing any repository code

The trigger-based conflict handling in §9.3–9.4 has real consequences for how the Java layer must be written. These aren't optional style preferences — get them wrong and the app will silently disagree with the database about what happened.

- **`spring.jpa.hibernate.ddl-auto` must be `none` (or at most `validate`) — never `update` or `create`.** Flyway owns the schema now, including the triggers and their `ENABLE ALWAYS` flags. If Hibernate is ever allowed to auto-generate or alter schema, it has no concept of triggers and can silently diverge sites from each other, which is exactly the DDL-is-not-replicated failure mode called out in §9.4.
- **Don't add JPA `@Version` optimistic locking to `TripState`/`ContainerState` entities.** The database trigger is already the single source of truth for conflict resolution (Lamport-clock + site tiebreak), and it works by silently discarding a losing `UPDATE` (0 rows affected, no exception). JPA's own `@Version` mechanism expects a *different* kind of conflict signal (a thrown `OptimisticLockException` on mismatch) and will just get confused sitting on top of a trigger that resolves things a different way. Let the DB be the sole authority here.
- **Because the trigger can silently drop your update, JPA's `save()` won't tell you it happened.** `save()`/`saveAndFlush()` return the entity you *tried* to persist, not what's actually in the row — if the trigger returned `NULL`, the row didn't change, but JPA has no built-in way to notice that. For any write to a State fragment where the app needs to know whether it actually won, use a raw `JdbcTemplate`/`@Modifying` query and check the affected-row count, or re-`SELECT` immediately after the write. This matters most in the Handover flow, where the API needs to know if the ownership-token acquisition actually succeeded.
- **One Maven project, one Docker image, four deployments.** Every site runs the same Spring Boot application logic — the only thing that differs is which local Postgres it points at. Use a Spring profile per site (`application-depot.yml`, `application-border.yml`, `application-port.yml`, `application-destination.yml`), each with its own `spring.datasource.url` pointing at that namespace's Postgres service, and select the active profile via `SPRING_PROFILES_ACTIVE` in the Kubernetes Deployment manifest. This avoids four teams accidentally writing four diverging codebases.
- **Flyway migrations live in the same image, applied independently at every site.** Put versioned `.sql` files (including the `CREATE TRIGGER` / `ALTER TABLE ... ENABLE ALWAYS TRIGGER` statements from §9.3–9.4) under `src/main/resources/db/migration/`. Because the exact same JAR/image is deployed to all four namespaces, Flyway running on startup at each site is what guarantees identical schema everywhere — this is the practical answer to "DDL isn't replicated," and it comes essentially for free once Flyway + Spring Boot are wired up.

---

## 10. Authentication & Authorization

"Minimal UI" (per the handout's constraint) is a statement about visual polish, not about the Security non-functional requirement. Every actor listed in the handout still needs to be authenticated, and every write still needs to be authorized against a role — a plain HTML form submitting to an unprotected endpoint fails Security regardless of how little CSS it has.

The actors split into two categories that need genuinely different treatment, and conflating them is the most likely mistake here:

### 10.1 Site-bound actors — local, offline-capable authentication

The Depot dispatcher/clerk, Border liaison, and Port agent each work at exactly one physical site and never need to authenticate anywhere else. For these:

- Each site's Spring Boot application has its own local Spring Security user store, backed by a `users`/`roles` table in **that site's own Postgres instance** — not replicated, not shared, not dependent on any other site being reachable.
- Roles map directly to the handout's actors: `DISPATCHER`, `CLERK`, `LIAISON`, `AGENT`. Enforce with method-level `@PreAuthorize` annotations on the relevant REST controller methods (e.g. only `LIAISON` can write a `Handover` at a Border Post node).
- Because authentication is entirely local to the site's own database, this works correctly even when that site is completely disconnected from every other site — consistent with the offline-first requirement. A clerk can still log in and record a gate-in during a total network outage.

### 10.2 Cross-cutting actors — signed tokens, verified offline everywhere

Drivers move between sites; Customer Service and Management need a view across all sites (i.e., they authenticate against Control Tower, not a single operational site). Neither fits a "local user table per site" model cleanly — but a live call to a central auth server on every request would reintroduce exactly the single point of failure the whole architecture exists to avoid.

**Fix: asymmetric-signed JWTs, verified locally at every site with no live call back to the issuer.**

- A single issuer — naturally Depot, since it's already the fixed owner of `Driver_Profile` in the Master fragment (§2.3) — holds the **private** signing key and issues JWTs to drivers at onboarding time (e.g. `RS256`).
- The corresponding **public** key is distributed to every site ahead of time (baked into each site's Kubernetes Secret at deploy time, same mechanism already used for DB credentials in §9.5). Every site can independently verify a driver's JWT signature completely offline — no network call to Depot required at verification time, only at initial issuance.
- This means a driver's credentials, once issued, work at Border, Port, or Destination even if that site has been completely cut off from Depot for hours — the same resilience property already proven for data in the Test 2/Test 4 replication testing.
- Customer Service and Management authenticate the same way but against **Control Tower** specifically, since that's the only node with the full cross-site read view their roles need. `CUSTOMER_SERVICE` should be scoped read-only and ideally filtered to only the client(s) they're servicing; `MANAGEMENT` gets broader read access for audit/KPI purposes but — consistent with §2.4 — still never needs write access to any operational fragment.

### 10.3 Role summary

| Role | Where authenticated | Access |
|---|---|---|
| `DISPATCHER` | Local (Depot) | Read/write Depot's own Trip/Assignment data |
| `CLERK` | Local (Depot) | Read/write gate-in/out, seal checks at Depot |
| `LIAISON` | Local (Border) | Read/write clearance Milestones, Handovers at Border |
| `AGENT` | Local (Port) | Read/write port Milestones at Port |
| `DRIVER` | JWT, issued by Depot, verified everywhere offline | Write status confirmations at whichever site they're currently at |
| `CUSTOMER_SERVICE` | JWT, verified at Control Tower | Read-only, scoped to their own client(s) |
| `MANAGEMENT` | JWT, verified at Control Tower | Read-only, full cross-site view + KPIs |

### 10.4 Why this isn't scope creep

This maps directly onto infrastructure you've already built and tested, rather than adding a new independent subsystem:

- Site-local auth reuses the exact same "local Postgres, no cross-site dependency" property already proven for `trip_state` writes in the replication testing.
- Driver JWT verification reuses the exact same "public key distributed in advance, no live call needed" pattern already used conceptually for the Master fragment being replicated ahead of time rather than fetched on demand.
- Both are small, well-understood additions to a Spring Boot + Kubernetes stack you're already building — `spring-boot-starter-security` plus a JWT library (e.g. `jjwt`), no new infrastructure component required.



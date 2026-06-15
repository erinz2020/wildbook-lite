---
name: wildbook-lite-dev
version: 1.0.0
description: |
  wildbook-lite development guide. Architecture, package layout, the
  Spring Boot patterns this codebase demonstrates, write-path conventions,
  permission model, async/event flow, search, and common task recipes.
  Use when: working on the wildbook-lite Java backend — adding an entity,
  wiring a new endpoint, debugging cascade-delete order, touching auth,
  or extending the async ML pipeline.
metadata:
  author: erinzhang
  java: "17"
  spring-boot: "4.0.6"
  postgres: "15+"
---

# wildbook-lite Developer Skill

A teaching-oriented Spring Boot backend modeled on the real Wildbook
wildlife-tracking platform. Production-shaped patterns, not toy CRUD.

## Related Skills

- **wildbook-lite-database** — Postgres schema reference: table-by-table,
  FK map, cascade-delete order, common gotchas. Use when writing SQL,
  designing migrations, or chasing a constraint violation.
- **CLAUDE.md** at the repo root — interview-oriented learning protocol,
  task management rules (`javatasks.md`, `javaprogress.md`), the
  do-not-give-full-solution rule for hands-on tasks.

## Platform Overview

wildbook-lite mirrors the core entities of Wildme's Wildbook
([wildme/Wildbook](https://github.com/WildMeOrg/Wildbook)) but uses
modern Spring Boot 4 + Hibernate/JPA instead of Tomcat + DataNucleus
JDO. The shape stays faithful so backend lessons transfer; the stack
stays current so the patterns being learned are interview-relevant
today.

**Tech stack:** Java 17, Spring Boot 4.0.6 (Spring 7, Hibernate 7,
Spring Security 7), Postgres 15+, Caffeine cache, OpenSearch 2 (gated
behind `app.opensearch.enabled`), Flyway, Maven, Testcontainers.

**No frontend.** wildbook-lite is API-only — exposes JSON over JWT-protected
REST endpoints. Test surface is curl / Postman / E2E tests.

## Domain Model

```
Project ────┬──── Encounter ────┬──── MediaAsset
            │       │           │
            │       │           └──── Annotation ──── Feature ──── MediaAsset
            │       │                    │
            │       │                    └──── IaTask ──── MatchResult ──── MatchCandidate
            │       │                                                              │
            │       │                                                              ▼
            │       │                                                         Individual
            │       │
            │       ├──── Sighting (orphan obs that may roll up later)
            │       ├──── Comment
            │       ├──── EncounterStatusHistory  (workflow timeline)
            │       ├──── EncounterTag join
            │       ├──── Observer  (the human, may not have a User account)
            │       ├──── Individual  (the identified animal, M:1)
            │       ├──── Occurrence (group sighting event, M:1)
            │       └──── Taxonomy   (species reference, M:1)
            │
            ├──── ProjectMember (User ↔ Project ↔ ProjectRole)
            └──── User
                     ├──── RefreshToken (rotating)
                     └──── Notification (DB-persisted)

AuditLog (cross-cutting, all @Audited methods land here)
```

### Aggregates and their roots

| Aggregate root | What it owns | Where it lives |
|---|---|---|
| `Encounter` | sightings, comments, tags, status history, media, annotations | `entity/`, `service/EncounterService` |
| `Occurrence` | reverse view of N encounters of a group event | `occurrence/` |
| `Annotation` | Features (Cascade.ALL + orphanRemoval) | `annotation/` |
| `IaTask` | MatchResult → MatchCandidate (Cascade.ALL) | `ml/` |
| `Project` | ProjectMember rows | `project/` |
| `User` | RefreshToken rows | `auth/` |
| `Taxonomy` | reference data only | `taxonomy/` |

### Key entities at a glance

| Entity | File | Notes |
|---|---|---|
| `Encounter` | `entity/Encounter.java` | Central. FKs to project, individual, observer, occurrence, taxonomy. Holds workflow status + denormalized species. |
| `Individual` | `entity/Individual.java` | Identified animal. Cascade.ALL over encounters (think long-lived parent). |
| `Sighting` | `entity/Sighting.java` | Orphan observation — may be re-parented to an Encounter via report flow. |
| `Annotation` | `annotation/Annotation.java` | Bounding box on a MediaAsset, plus species/viewpoint/exemplar. Bridge to MediaAsset via `Feature`. |
| `Feature` | `annotation/Feature.java` | Bridge entity. `BBOX` or `TRIVIAL` (whole-image placeholder). |
| `Occurrence` | `occurrence/Occurrence.java` | Group sighting event. Cardinality 1 → N Encounters. |
| `Taxonomy` | `taxonomy/Taxonomy.java` | Species catalogue. UNIQUE on scientificName. Admin-only writes. |
| `IaTask` / `MatchResult` / `MatchCandidate` | `ml/` | Async ML pipeline stub. |

## Package Layout

```
com.wildme.wildbook_lite
├── annotation/        — Annotation + Feature aggregate (B)
├── audit/             — @Audited aspect + AuditLog entity + read endpoints
├── auth/              — User, Role, JWT, refresh tokens, @CurrentUser
├── comment/           — Comments on Encounters
├── common/            — @Audited annotation, ForbiddenException, custom validators
├── config/            — SecurityConfig, AsyncConfig, AppProperties, schedulers,
│                       Caffeine cache, OpenSearch config, MeterRegistry hooks
├── controller/        — Top-level REST controllers (legacy entry point;
│                       newer features keep controllers inside their feature pkg)
├── dto/               — Cross-cutting DTOs (CreateEncounterRequest etc.)
├── encounter/         — EncounterStatus state machine + EncounterStatusHistory
├── entity/            — Shared/older entities (Encounter, Individual, Sighting,
│                       MediaAsset, Observer)
├── exception/         — BusinessException (400), NotFoundException (404)
├── ml/                — Async ML pipeline (E) — IaTask + Runner + Match*
├── notification/      — Notification entity + async event listeners
├── occurrence/        — Occurrence aggregate (A)
├── project/           — Project + ProjectMember + ProjectGuard SpEL bean
├── repository/        — Shared/older Spring Data repos
├── search/            — Postgres FTS (search/) + OpenSearch (search/opensearch/)
├── service/           — Shared/older services (EncounterService at the centre)
├── stats/             — Project-level statistics endpoint
├── storage/           — AssetStore strategy (LocalAssetStore / S3AssetStore)
├── tag/               — Tags on encounters (M:N via join table)
└── taxonomy/          — Taxonomy reference data (C)
```

**Newer features keep everything in one package** (entity + repo + service
+ controller + dto) instead of splitting across `entity/`,
`repository/`, etc. When extending, prefer the new layout — it's
easier to read and easier to delete.

## Spring Boot Patterns This Codebase Demonstrates

A non-exhaustive cheat sheet of "where to look when you want to see
pattern X in action":

| Pattern | Where to look |
|---|---|
| Constructor injection only | every `@Service` / `@Component` |
| `@ConfigurationProperties` records with `@Validated` | `config/AppProperties.java` |
| `@Profile` for env-specific beans | `storage/LocalAssetStore`, `storage/S3AssetStore` |
| `@CurrentUser` HandlerMethodArgumentResolver | `auth/CurrentUserArgumentResolver` |
| HandlerInterceptor (request logging) | `config/RequestLoggingInterceptor` |
| TraceId in MDC via servlet filter | `config/TraceIdFilter` |
| Fixed-window rate limiter | `config/RateLimitFilter` |
| Custom Actuator `@Endpoint` | `config/DomainActuatorEndpoint` |
| Custom HealthIndicator | `config/AssetStoreHealthIndicator` |
| `MeterRegistry` for Prometheus counters | `auth/AuthMetrics` |
| `@Scheduled` with `@ConditionalOnProperty` | `auth/RefreshTokenCleanupJob`, `notification/NotificationCleanupJob` |
| `@Async` with named executor | `ml/IaTaskRunner`, `notification/NotificationListener` |
| `@Retryable` (Spring Retry) | search-side index retries (look in `search/opensearch/`) |
| `ApplicationRunner` for boot-time seed | `config/DataSeedRunner` |
| CORS config | `config/CorsConfig` |
| Caffeine `@Cacheable` / `@CacheEvict` | `service/EncounterService.findById` |
| `@PreAuthorize` SpEL + bean | `project/ProjectGuard` invoked via `@projectGuard.canRead(#projectId)` |
| `@PreAuthorize("hasRole('ADMIN')")` method-level | `taxonomy/TaxonomyController`, `auth/UserController` |
| `@Audited` aspect | `common/Audited` annotation + `audit/` aspect |
| ApplicationEventPublisher + `@TransactionalEventListener(AFTER_COMMIT)` | `notification/NotificationListener`, `search/opensearch/EncounterIndexerListener` |
| Async job pattern (enqueue → 202 → poll) | `ml/IaTaskController`, `ml/IaTaskService.enqueue` |
| Long-running cursor streaming | `repository/EncounterRepository.streamByProjectId` (`@QueryHints` for fetch size) |
| Per-row REQUIRES_NEW for bulk best-effort ops | `service/EncounterBulkService` (with `@Lazy` self-injection — see footgun below) |
| Optimistic locking | `@Version` on every aggregate root entity |
| OpenSearch `@ConditionalOnProperty` gating | every bean in `search/opensearch/` |

## Permission Model

Two layers stacked:

```
1. Spring Security global filter chain (auth/JwtAuthenticationFilter)
       ↓ resolves JWT → AppPrincipal → Authentication
2. Method-level checks:
   - @PreAuthorize("hasRole('ADMIN')")             — admin-only writes
   - @PreAuthorize("@projectGuard.canRead(#id)")   — project-scoped reads
   - In-service ProjectGuard calls                  — when SpEL can't see the projectId
```

### ProjectGuard ladder

```
VIEWER  → canRead()    — see encounters / occurrences / annotations / search
EDITOR  → canWrite()   — create + PATCH + transition + comment + assign
OWNER   → canManage()  — project settings, member roles, REVIEWED → PUBLISHED transition
```

`ProjectGuard.hasAtLeast(projectId, ProjectRole.OWNER)` is the underlying
primitive. The state machine (`encounter/EncounterStatus`) declares the
minimum role for each transition in its lookup table.

### Don't reach for entity navigation in permission checks

The `Encounter.submitterUserId` / `assignedToUserId` columns are
deliberately `Long` not `@ManyToOne User`. Letting JPA traverse to User
from a permission check creates lazy-init traps and bad N+1 in list
endpoints. Look up via the `auth/` services instead if you need the
user record.

## Write-Path Conventions

Every write goes through a service method that:

1. Resolves entities with `findById(...).orElseThrow(NotFoundException)`.
2. Runs the relevant `ProjectGuard.canWrite/canManage(projectId)` check (or `requireWriteAccess(e)` helper).
3. Validates business rules (species coherence, occurrence-same-project, etc.) — fail fast with `BusinessException`.
4. Mutates and saves.
5. Appends to relevant history (e.g., `EncounterStatusHistory`) and publishes events.
6. Returns the persisted entity (or DTO).

Annotations on the method:
- `@Audited("entity.action")` — picked up by `AuditAspect` → writes to `audit_log` table.
- `@Transactional` — wraps the whole operation. Read-only ops use `(readOnly = true)`.
- `@CacheEvict(value = "encounter", key = "#id")` — when the cached `findById` could go stale.

Events to know about:
- `EncounterCreatedEvent`, `EncounterAssignedEvent`, `EncounterPublishedEvent` → fan out to `notification/`.
- `EncounterChangedEvent(id, UPSERT|DELETE)` → fan out to `search/opensearch/EncounterIndexerListener` (only fires after commit).

## Async / Event Flow

```
controller
   ↓
service @Transactional
   │  emits ApplicationEventPublisher#publishEvent(...)
   │
   ↓ (commit boundary)
@TransactionalEventListener(phase = AFTER_COMMIT)  ← never fires on rollback
   ↓
@Async("applicationTaskExecutor")
   ↓
runs on the wildbook-async-* thread pool
```

**Footgun:** calling an `@Async` method on `this` from the SAME bean
bypasses the Spring proxy and runs synchronously. `ml/IaTaskService` ↔
`ml/IaTaskRunner` are split into two beans for exactly this reason. If
you ever add a new async method, put it on a new component.

The same footgun applies to per-row `@Transactional(propagation =
REQUIRES_NEW)` — see `service/EncounterBulkService`, which uses `@Lazy`
self-injection so calls go through the proxy.

## Encounter State Machine

```
DRAFT ── (EDITOR+) ──→ REVIEWED ── (OWNER) ──→ PUBLISHED ── (OWNER) ──→ ARCHIVED
   ↑                       │                                              │
   └──── (OWNER) ───────────┘                                              │
   └────────────────── (OWNER) — re-open from ARCHIVED ─────────────────── ┘
```

Defined as a lookup table in `encounter/EncounterStatus.java`. To add a
new arrow, append a `Transition` to the static `TABLE` — that's the
only place. Do NOT branch on status in service code; let
`EncounterService.transition(id, toStatus)` drive everything.

## IA Pipeline (E)

```
1. POST  /api/ia-tasks { annotationId }
       → service.enqueue() saves IaTask{status=PENDING}
       → runner.run(id) dispatched on async pool
       → returns 202 + body { id, status: "PENDING" }

2. Runner (separate bean, @Async):
       markRunning(id)       [REQUIRES_NEW tx]
       sleep 1500ms           (stub for real ML)
       computeCandidates(id)  [REQUIRES_NEW, readOnly]
       markDone(id, list)     [REQUIRES_NEW tx] — sets MatchResult + status=DONE
       (on Exception: markFailed(id, msg))

3. Client polls GET /api/ia-tasks/{id} until status is terminal.
```

State machine: `PENDING → RUNNING → DONE | FAILED`, plus user-initiated
`PENDING → CANCELLED`. Anything else is a 400.

## Search

Two stacks, both behind separate endpoints — both can coexist.

| Path | Backend | When to use |
|---|---|---|
| `/api/search/encounters?q=` | Postgres FTS (`to_tsvector`) | always on, no infra needed |
| `/api/opensearch/encounters?q=` | OpenSearch 2 | richer queries, gated on `app.opensearch.enabled` |

OpenSearch sync is event-driven:
- `EncounterChangedEvent(id, UPSERT|DELETE)` → `EncounterIndexerListener` (AFTER_COMMIT, @Async).
- Bulk reindex available at `POST /api/admin/reindex/encounters`.
- Index mapping in `search/opensearch/EncounterIndexer.ensureIndex()`. Text fields get a `.raw` keyword sub-field for exact filtering (`species` analyzed AND `species.raw` exact match).

## Common Recipes

### Add a new entity in its own feature package

```
src/main/java/com/wildme/wildbook_lite/widget/
├── Widget.java                 — @Entity with @Version
├── WidgetRepository.java       — extends JpaRepository
├── WidgetService.java          — @Service with ProjectGuard injected
├── WidgetController.java       — @RestController with @RequestMapping("/api/widgets")
└── dto/
    ├── CreateWidgetRequest.java   — record + @Valid
    ├── UpdateWidgetRequest.java   — record, all fields nullable for PATCH
    └── WidgetResponse.java        — record + static `from(Widget)` factory
```

Checklist when adding:
- [ ] `@Version` on entity (optimistic locking)
- [ ] `@Index` on every FK column and every column you'll filter by
- [ ] `@Enumerated(STRING)` on every enum field — NEVER `ORDINAL`
- [ ] `@Audited("widget.action")` on write methods
- [ ] `ProjectGuard.canRead/canWrite` on every endpoint
- [ ] DTO factory `from(Entity)`, not entity returned directly (if the entity has lazy collections)
- [ ] Update the cascade-delete chain if Encounter or any parent should also drop this child (see `EncounterService.deleteById`)

### Add a write that needs to invalidate cache

```java
@Audited("widget.update")
@CacheEvict(value = "widget", key = "#id")
@Transactional
public Widget update(Long id, UpdateWidgetRequest req) { ... }
```

### Add a write that should trigger async work

```java
@Transactional
public Widget create(...) {
    Widget saved = repo.save(...);
    events.publishEvent(new WidgetCreatedEvent(saved.getId(), ...));
    return saved;
}
```

Then add a `@Component` listener:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("applicationTaskExecutor")
public void onWidgetCreated(WidgetCreatedEvent e) { ... }
```

AFTER_COMMIT means a rollback won't fire phantom side-effects.

### Add a bulk-best-effort endpoint

Read `service/EncounterBulkService`. Pattern:

- Outer service method gets a list of ids, builds a `BulkResult`.
- For each id, call `self.processOne(id)` (via `@Lazy` self-injection).
- `processOne` is `@Transactional(propagation = REQUIRES_NEW)` so its rollback doesn't kill siblings.
- `@Audited("entity.bulkAction")` on the outer method.

### Add a cascade-delete child

```java
// In ChildRepository
@Modifying
@Query("delete from Child c where c.encounter.id = :encounterId")
int deleteByEncounterId(@Param("encounterId") Long encounterId);
```

Then add the call to `EncounterService.deleteById` in the right
position. **Order matters** because of FK constraints:

1. Children that point AT children (e.g., Feature → Annotation → Encounter): grandchildren first.
2. Direct children (Sighting, Comment, MediaAsset, Annotation, ...).
3. `encRepo.flush()` — force Hibernate to emit the bulk deletes BEFORE the parent delete.
4. `encRepo.delete(encounter)`.

Already wired today: `feature → annotation → media → sighting → comment → tag → history → encounter`.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `LazyInitializationException` in controller | DTO mapping after the tx closed | Either return entity (Spring opens session-in-view) OR pre-touch the lazy collection inside the @Transactional service method: `e.getKids().size();` |
| `@Async` method runs synchronously | Self-invocation — calling `@Async` method on `this` inside the same bean | Split into separate bean OR `@Lazy` self-inject |
| `@Transactional(REQUIRES_NEW)` doesn't actually start a new tx | Same proxy bypass as above | `@Lazy` self-injection |
| `@PreAuthorize` returns 500 instead of 403 | SpEL referenced a `@beanName` that isn't a Spring bean | Make sure `@Component("name")` is set |
| FK violation on `DELETE encounter` | Bulk @Modifying ran in the wrong order, or didn't `encRepo.flush()` first | Re-check the cascade order in `EncounterService.deleteById` |
| OpenSearch endpoints 404 even though OS is up | Bean conditional gate not satisfied | Set `app.opensearch.enabled=true` in `application-dev.yml` |
| `mvn verify` fails — no Docker | Testcontainers IT tests need Docker | Run `./mvnw test` instead (Surefire excludes `*IT.java`) |
| New tests break old `JwtServiceTest` | `AppProperties` ctor grew a new component | Add the new component to the manual `new AppProperties(...)` in `JwtServiceTest.setUp()` |

## Build / Run

| Command | What it does |
|---|---|
| `./mvnw -q -DskipTests compile` | Quick compile check |
| `./mvnw test` | Unit tests (no Docker) |
| `./mvnw verify` | Integration tests too (needs Docker) |
| `./mvnw spring-boot:run` | Run locally on :8080 |
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` | Local dev profile (data seed runs) |

Postgres must already be running locally (no Docker — see CLAUDE.md
training rules). Schema is auto-managed by `ddl-auto: update` for now;
Flyway is available but not wired into the dev flow.

## File Size Budgets

- Java files: aim for < 300 lines, accept < 800. `EncounterService` is
  the densest at ~600 and earning every line — when adding to it, ask
  "should this be a new component instead?"
- Controllers: < 200 lines each.
- DTOs: one record per file. ~30 lines is normal.

## Where Not To Add Code

- `entity/`, `repository/`, `service/`, `controller/`, `dto/` —
  shared/legacy packages from earlier weeks. New features should
  live in their own feature package (`occurrence/`, `annotation/`,
  `ml/`, etc.).
- `WildbookLiteApplication.java` is just `@SpringBootApplication` —
  don't put main logic there.

## Reference: Where the Biggest Pieces Live

| Concern | File |
|---|---|
| Security config | `config/SecurityConfig.java` |
| JWT lifecycle | `auth/JwtService.java` + `auth/RefreshTokenService.java` |
| All async dispatch | `config/AsyncConfig.java` |
| All event listeners | `notification/NotificationListener.java`, `search/opensearch/EncounterIndexerListener.java` |
| Encounter write surface | `service/EncounterService.java` |
| Encounter delete cascade | `service/EncounterService.deleteById` |
| State machine | `encounter/EncounterStatus.java` |
| OpenSearch mapping | `search/opensearch/EncounterIndexer.ensureIndex` |
| ML pipeline | `ml/IaTaskRunner.java` |
| Audit aspect | `audit/AuditAspect.java` |
| Permission bean | `project/ProjectGuard.java` |

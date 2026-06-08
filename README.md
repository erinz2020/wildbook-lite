# wildbook-lite

A Spring Boot backend modelled on Wildbook — a real-world wildlife tracking
platform where researchers log animal encounters, group them into projects,
attach photos, comment, search, and collaborate.

The codebase is deliberately built as a teaching/portfolio project: every
non-trivial decision (auth, AOP, caching, async events, streaming export,
full-text search, rate limiting) is picked because it maps to a
high-frequency backend interview topic.

---

## What it does

- **Auth**: register / login with username + password, get back a short-lived
  JWT **access token** and a longer-lived DB-stored **refresh token**.
  Refresh rotates (single-use) and logout revokes all refresh tokens.
- **Users**: every authenticated user can read/update their own profile;
  ADMINs can list users.
- **Projects**: group encounters together. Membership is explicit, with
  three levels: `VIEWER` < `EDITOR` < `OWNER`. Permissions are enforced
  both by `@PreAuthorize` SpEL and by service-layer guards.
- **Encounters**: record a single sighting (species, location, date,
  notes) scoped to a project. Can be assigned to an `Individual` (a
  specific tracked animal) and witnessed by an `Observer`.
- **Tags**: each project has its own tag set; encounters can be tagged
  and queried by tag.
- **Media**: photos attach to encounters via multipart upload, stored on
  disk through a Strategy-pattern `AssetStore` (swap in S3 later).
- **Comments**: nested sub-resource of encounters. Only the author can delete.
- **Notifications**: real DB-persisted notifications. After an encounter is
  created, every other project member gets an in-app notification. Has
  list / unread-count / mark-as-read endpoints.
- **Search**: Postgres full-text search across species, location, and notes,
  with ts_rank ordering and websearch query syntax (quotes, OR, leading `-`).
- **Statistics**: per-project dashboard counts (encounters, members, media,
  comments) plus top species via native aggregate queries.
- **Bulk CSV import**: best-effort row-by-row import with per-row error
  reporting (`REQUIRES_NEW` per row).
- **Streaming CSV export**: constant-memory streaming download with
  `StreamingResponseBody` + JPA cursor.
- **Audit log**: every `@Audited` service method writes one row to the
  `audit_log` table (success/fail/user/duration/trace id), via AOP + an
  async event listener.
- **Observability**: Spring Boot Actuator with custom `AssetStoreHealthIndicator`,
  per-request `X-Request-Id` propagated through the SLF4J MDC.
- **Rate limiting**: in-process per-IP fixed window on `/api/auth/login`
  and `/api/auth/register` to slow credential stuffing.
- **CORS**: configurable allowed origins for browser-based frontends.

---

## Tech stack

| Layer            | Choice                                                           |
| ---------------- | ---------------------------------------------------------------- |
| Language         | Java 17                                                          |
| Framework        | Spring Boot 4.0.x (Spring 7, Hibernate 7, Spring Security 7)     |
| Build            | Maven (`./mvnw`)                                                 |
| Database         | PostgreSQL 16                                                    |
| Migrations       | Flyway (declared, currently disabled — JPA `ddl-auto=update`)    |
| Auth             | Stateless JWT access tokens + DB-stored rotating refresh tokens  |
| Cache            | Caffeine (in-process, TTL + size cap)                            |
| AOP              | Spring AOP via `spring-boot-starter-aspectj`                     |
| Async            | `@Async` + `ApplicationEventPublisher` on a custom thread pool   |
| Observability    | Spring Boot Actuator + custom HealthIndicator + MDC trace IDs    |
| File storage     | Local disk by default; `AssetStore` interface lets S3 plug in    |
| Validation       | Jakarta Validation + custom `@ValidSpecies`                      |
| Tests            | (TODO — JUnit 5 + Mockito + Testcontainers)                      |

---

## Architecture

```
                    +-----------------------------------------+
HTTP request -----> | TraceIdFilter (sets X-Request-Id / MDC) |
                    | CorsFilter                              |
                    | RateLimitFilter (on /api/auth/*)        |
                    | JwtAuthenticationFilter                 |
                    | UsernamePasswordAuthenticationFilter    |
                    +----------------+------------------------+
                                     |
                                     v
                    +-----------------------------------------+
                    | Controller (@PreAuthorize SpEL)         |
                    +----------------+------------------------+
                                     |
                                     v
                    +-----------------------------------------+
                    | Service (@Transactional, @Cacheable,    |
                    |          @Audited, ProjectGuard checks) |
                    +----------------+------------------------+
                                     |
                                     v
                    +-----------------------------------------+
                    | Repository (Spring Data JPA)            |
                    +----------------+------------------------+
                                     |
                                     v
                    +-----------------------------------------+
                    | PostgreSQL                              |
                    +-----------------------------------------+

  Side channels (off the request thread):
   --- ApplicationEvent (AFTER_COMMIT) ---> NotificationListener writes Notification rows
   --- ApplicationEvent (immediate)    ---> AuditLogListener (REQUIRES_NEW) writes AuditLog rows
```

### Package layout

```
com.wildme.wildbook_lite/
├── WildbookLiteApplication.java
├── auth/             User, Role, JWT, RefreshToken, AuthController, UserController
├── project/          Project, ProjectMember, ProjectGuard
├── comment/          nested under encounters
├── tag/              Tag, EncounterTag (per-project tag set)
├── notification/     Notification (DB-persisted) + async listener
├── audit/            AuditLog + async listener (DB-persisted audit trail)
├── search/           Postgres FTS controller + service
├── stats/            project stats endpoints
├── common/           BaseEntity, AuditAspect, @Audited, @ValidSpecies,
│                     PageResponse, CsvWriter, ForbiddenException
├── config/           SecurityConfig, JpaConfig, AsyncConfig, CacheConfig,
│                     CorsConfig, TraceIdFilter, RateLimitFilter,
│                     AssetStoreHealthIndicator
├── controller/       (legacy layered) encounter, individual, observer,
│                     sighting, media, export, bulk import
├── service/          (legacy layered)
├── repository/       (legacy layered)
├── dto/              (legacy layered)
├── entity/           (legacy layered, pre-BaseEntity)
├── exception/        GlobalExceptionHandler + domain exceptions
└── storage/          AssetStore + LocalAssetStore (Strategy pattern)
```

> The project is mid-migration from a classical layered structure
> (`controller/`, `service/`, etc.) to feature-based packages
> (`auth/`, `project/`, `comment/`, `tag/`, ...). New features live in
> feature-based packages; older entities still sit under their layer
> directories. Both patterns are intentionally visible side-by-side.

---

## Quick start

### 1. Postgres

Make sure Postgres is running on `localhost:5432` with the `wildbook`
database + user:

```bash
psql postgres <<'EOF'
CREATE USER wildbook WITH PASSWORD 'wildbook';
CREATE DATABASE wildbook OWNER wildbook;
GRANT ALL PRIVILEGES ON DATABASE wildbook TO wildbook;
EOF
```

A `docker-compose.yml` is provided as an alternative.

### 2. Build and run

```bash
./mvnw spring-boot:run
```

Listens on `http://localhost:8080`. On first boot, JPA creates all tables
via `ddl-auto=update`.

### 3. End-to-end smoke test

```bash
# Register and capture the JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"password123"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')

# Profile
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/users/me

# Create a project — creator becomes OWNER automatically
PROJ=$(curl -s -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Hawaii Humpbacks 2026"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')

# Create encounters (each one publishes an async EncounterCreatedEvent and writes one AuditLog row)
curl -s -X POST http://localhost:8080/api/encounters \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"projectId\":$PROJ,\"species\":\"Humpback whale\",\"location\":\"Maui\",\"notes\":\"calf and mother\"}"

curl -s -X POST http://localhost:8080/api/encounters \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"projectId\":$PROJ,\"species\":\"Spinner dolphin\",\"location\":\"Oahu\",\"notes\":\"pod of 20\"}"

# Full-text search
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/search/encounters?projectId=$PROJ&q=humpback"

# Project stats
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/projects/$PROJ/stats"

# Tags
TAG=$(curl -s -X POST "http://localhost:8080/api/projects/$PROJ/tags" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"rare","color":"#e74c3c"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/encounters/1/tags/$TAG"

# Bulk CSV import
echo 'species,location,notes
Hawaiian monk seal,Lanai,resting on beach
Green sea turtle,Maui,foraging in reef
,Maui,bad row missing species' > /tmp/import.csv

curl -X POST -H "Authorization: Bearer $TOKEN" \
  -F file=@/tmp/import.csv \
  "http://localhost:8080/api/encounters/import?projectId=$PROJ"
# returns: {"totalRows":3,"succeeded":2,"failed":1,"errors":[{"rowNumber":4,"reason":"species is required"}]}

# Streaming CSV export
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/encounters/export.csv?projectId=$PROJ" \
  -o encounters.csv

# Notifications inbox
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/notifications/unread-count

# Refresh token rotation
REFRESH=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["refreshToken"])')

curl -s -X POST http://localhost:8080/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}"

# Logout (revokes ALL refresh tokens for this user)
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/auth/logout

# Observability
curl http://localhost:8080/actuator/health           # public
curl http://localhost:8080/actuator/health/assetStore # public, custom indicator
```

---

## Key API endpoints

### Auth
| Method | Path                          | Auth   | Note                        |
| ------ | ----------------------------- | ------ | --------------------------- |
| POST   | `/api/auth/register`          | public | rate-limited                |
| POST   | `/api/auth/login`             | public | rate-limited                |
| POST   | `/api/auth/refresh`           | public | rotates the refresh token   |
| POST   | `/api/auth/logout`            | bearer | revokes all refresh tokens  |

### Users
| Method | Path                          | Auth   | Note                          |
| ------ | ----------------------------- | ------ | ----------------------------- |
| GET    | `/api/users/me`               | bearer | full self profile             |
| PATCH  | `/api/users/me`               | bearer | update email or password      |
| GET    | `/api/users/{id}`             | bearer | public profile of another user|
| GET    | `/api/users`                  | ADMIN  | paginated list of all users   |

### Projects
| Method | Path                                          | Auth                        |
| ------ | --------------------------------------------- | --------------------------- |
| POST   | `/api/projects`                               | bearer                      |
| GET    | `/api/projects`                               | bearer (my memberships)     |
| GET    | `/api/projects/{id}`                          | bearer + project read       |
| GET    | `/api/projects/{id}/members`                  | bearer + project read       |
| POST   | `/api/projects/{id}/members`                  | bearer + project manage     |
| DELETE | `/api/projects/{id}/members/{userId}`         | bearer + project manage     |
| GET    | `/api/projects/{id}/stats`                    | bearer + project read       |

### Encounters
| Method | Path                                          | Auth                        |
| ------ | --------------------------------------------- | --------------------------- |
| GET    | `/api/encounters?projectId=...`               | bearer + project read       |
| POST   | `/api/encounters`                             | bearer + project write      |
| GET    | `/api/encounters/{id}` (cached)               | bearer + project read       |
| PATCH  | `/api/encounters/{id}`                        | bearer + project write      |
| DELETE | `/api/encounters/{id}`                        | bearer + project write      |
| POST   | `/api/encounters/import?projectId=...`        | bearer + project write      |
| GET    | `/api/encounters/export.csv?projectId=...`    | bearer + project read       |

### Tags
| Method | Path                                          | Auth                        |
| ------ | --------------------------------------------- | --------------------------- |
| POST   | `/api/projects/{id}/tags`                     | bearer + project write      |
| GET    | `/api/projects/{id}/tags`                     | bearer + project read       |
| DELETE | `/api/projects/{id}/tags/{tagId}`             | bearer + project write      |
| POST   | `/api/encounters/{eid}/tags/{tagId}`          | bearer + project write      |
| DELETE | `/api/encounters/{eid}/tags/{tagId}`          | bearer + project write      |
| GET    | `/api/encounters/{eid}/tags`                  | bearer + project read       |

### Search
| Method | Path                                                            | Auth                  |
| ------ | --------------------------------------------------------------- | --------------------- |
| GET    | `/api/search/encounters?projectId=...&q=...&limit=20`           | bearer + project read |

### Comments
| Method | Path                                                  | Auth              |
| ------ | ----------------------------------------------------- | ----------------- |
| GET    | `/api/encounters/{id}/comments`                       | bearer            |
| POST   | `/api/encounters/{id}/comments`                       | bearer            |
| DELETE | `/api/encounters/{id}/comments/{commentId}`           | bearer + author   |

### Media
| Method | Path                                  | Auth                        |
| ------ | ------------------------------------- | --------------------------- |
| POST   | `/api/encounters/{id}/media`          | bearer + project write      |
| GET    | `/api/encounters/{id}/media`          | bearer + project read       |

### Notifications
| Method | Path                                  | Auth   |
| ------ | ------------------------------------- | ------ |
| GET    | `/api/notifications?unreadOnly=true`  | bearer |
| GET    | `/api/notifications/unread-count`     | bearer |
| POST   | `/api/notifications/{id}/read`        | bearer |
| POST   | `/api/notifications/read-all`         | bearer |

### Observability
| Method | Path                          | Auth   |
| ------ | ----------------------------- | ------ |
| GET    | `/actuator/health`            | public |
| GET    | `/actuator/health/assetStore` | public |
| GET    | `/actuator/info`              | public |
| GET    | `/actuator/metrics`           | bearer |

Errors come back in a unified envelope:
```json
{ "timestamp": "2026-06-08T15:00:00Z", "status": 403, "error": "No read access to project: 1" }
```
Responses also include `X-Request-Id`; every log line for that request carries the same trace id in MDC.

---

## Notable design decisions

| Decision                                            | Why                                                                                                                                                  |
| --------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ProjectMember` / `EncounterTag` are first-class entities, not `@ManyToMany` join tables | The relationship carries data (role + audit columns); `@ManyToMany` cannot model that cleanly.                                                       |
| `ProjectGuard` is a Spring bean exposed to SpEL via `@PreAuthorize("@projectGuard.canRead(#id)")` | Reusable, testable, and gives the cache layer somewhere to live later.                                                                                |
| **Access token (JWT) + DB-stored refresh token**    | Access tokens stateless (no central store); refresh tokens DB-stored so we have a revocation list (logout, password change). Refresh is single-use.    |
| **Refresh tokens stored as SHA-256 hash**           | Even a DB leak cannot be replayed against `/api/auth/refresh`.                                                                                       |
| Custom `AppPrincipal` carries `userId`              | Saves a DB round-trip per request to look the id up by username.                                                                                     |
| `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` | Side effects (notifications, emails) only fire if the originating transaction commits, and they never block the HTTP response.                       |
| **AOP audit → async event → REQUIRES_NEW DB write** | Audit rows are committed even when the originating tx rolled back (FAIL audits are exactly the ones we want!).                                       |
| In-process Caffeine cache, not Redis                | Single-node deploys do not need a network hop. The interface is `CacheManager`, so swapping in Redis later is a configuration change, not a rewrite. |
| Streaming CSV via `StreamingResponseBody` + JPA `Stream` + `fetch_size` | Memory stays flat for 10M-row exports. Naturally back-pressured via TCP → OutputStream → Hibernate cursor.                                            |
| Bulk import: per-row `REQUIRES_NEW`                 | Best-effort import — one bad row does not roll back successful rows. Returns per-row error report so users can re-upload only failures.              |
| Postgres FTS native query (not pg_trgm, not Elastic) | Stemming + stop-words + tokenization out of the box. Roadmap: a generated `tsvector` column + GIN index for production scale.                        |
| Fixed-window per-IP rate limit, in-process          | Slow credential stuffing without the operational cost of Redis. Multi-node deploys would lift this to Bucket4j + Redis.                              |
| `TraceIdFilter` → MDC → logback pattern             | Every log line in a request gets `[traceId=xxx]`; clients see and can quote `X-Request-Id` back when reporting bugs.                                 |
| `BaseEntity` + `@MappedSuperclass`                  | Centralises `id`, `createdAt`, `updatedAt`, `version` on every new entity (audit + optimistic locking out of the box).                              |

---

## Interview talking points

The features above map directly to common backend interview topics:

- **Spring core**: IOC, DI, bean lifecycle, AOP proxies (JDK vs CGLIB),
  `@Transactional` self-invocation pitfall, `@Async` self-invocation
  pitfall (and how the bulk import service uses self-injection to defeat it).
- **Spring Security**: filter chain order, stateless session, `UserDetailsService`,
  `@PreAuthorize` with SpEL bean references, defence in depth (annotation +
  service guard), why CORS must run before security.
- **JWT vs refresh tokens**: structure (header.payload.signature), HS256 vs RS256,
  why short access expiry + long refresh, rotation, revocation, hashing
  refresh tokens server-side.
- **JPA / Hibernate**: `@MappedSuperclass`, `@Version` optimistic locking,
  `@TransactionalEventListener`, JPA `Stream` with `fetch_size` for cursor-based reads,
  native queries for aggregates, when to drop down to native SQL.
- **Caching**: cache-aside pattern, TTL, three classic problems (penetration,
  breakdown, avalanche), Caffeine vs Redis trade-off.
- **Concurrency**: `ThreadPoolExecutor` 7 parameters, 4 rejection policies, why
  not `Executors.newFixedThreadPool`, MDC across thread boundaries.
- **AOP**: `@Around` advice, proxy mechanism, ordering, real use cases (audit,
  metrics, caching, transactions).
- **Database**: indexes (added via `@Index` on every hot column), HikariCP
  pool sizing, isolation levels, Postgres FTS (with the production GIN-index
  trade-off explained inline).
- **REST design**: nested sub-resources, unified error envelope, scope-based
  filtering, status-code semantics (401 vs 403 vs 404 vs 409 vs 429).
- **Security at scale**: IDOR prevention (every endpoint validates resource
  ownership), BCrypt for passwords, generic error messages, no stack traces
  in responses, rate limiting on auth endpoints, refresh-token rotation,
  password change revokes refresh tokens.
- **Observability**: structured logging, MDC trace propagation, custom
  Actuator HealthIndicator.
- **Reliability of side effects**: AFTER_COMMIT event listeners (notifications),
  REQUIRES_NEW for audit (so FAIL rows are still recorded).
- **Bulk operations**: per-row sub-transactions for partial-failure ergonomics.

---

## Roadmap

- [ ] Enable Flyway and stop relying on `ddl-auto=update`
- [ ] Refactor legacy entities (`Encounter`, `Individual`, `Observer`,
  `Sighting`, `MediaAsset`) to extend `BaseEntity`
- [ ] Generated `tsvector` column + GIN index on encounter for indexed FTS
- [ ] Unit tests with Mockito + integration tests with Testcontainers
- [ ] OpenAPI/Swagger UI once `springdoc-openapi` supports Spring Boot 4
- [ ] Async export job for very large datasets (S3 + signed-URL link)
- [ ] Token-bucket rate limiter and lift to Redis for multi-node deploys
- [ ] Refactor legacy layered packages (`controller/`, `service/`, etc.)
  into feature-based packages

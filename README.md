# wildbook-lite

A Spring Boot backend modelled on Wildbook — a real-world wildlife tracking
platform where researchers log animal encounters, group them into projects,
attach photos, and collaborate via comments.

The codebase is deliberately built as a teaching/portfolio project: every
non-trivial decision (auth, AOP, caching, async events, streaming export) is
picked because it maps to a high-frequency backend interview topic.

---

## What it does

- **Researchers register / log in**, get a JWT, and use it for every other call.
- **Projects** group encounters together. Membership is explicit, with three
  levels: `VIEWER` < `EDITOR` < `OWNER`. Permissions are enforced both by
  `@PreAuthorize` SpEL and by service-layer guards.
- **Encounters** record a single sighting (species, location, date, notes)
  scoped to a project. They can be assigned to an `Individual` (a specific
  tracked animal) and witnessed by an `Observer` (a field researcher).
- **Media (photos)** attach to encounters via multipart upload, stored on
  disk through a Strategy-pattern `AssetStore` (swap in S3 later by adding a
  second implementation).
- **Comments** are a nested sub-resource of encounters. Only the comment's
  author can delete it.
- **Notifications** fire asynchronously after an encounter is created
  (transactional event listener + dedicated thread pool).
- **CSV export** streams encounters of a project as `text/csv` with
  constant memory usage, regardless of dataset size.

---

## Tech stack

| Layer            | Choice                                                           |
| ---------------- | ---------------------------------------------------------------- |
| Language         | Java 17                                                          |
| Framework        | Spring Boot 4.0.x (Spring 7, Hibernate 7, Spring Security 7)     |
| Build            | Maven (`./mvnw`)                                                 |
| Database         | PostgreSQL 16                                                    |
| Migrations       | Flyway (declared, currently disabled — JPA `ddl-auto=update`)    |
| Auth             | Stateless JWT (`jjwt 0.12.x`, HS256)                             |
| Cache            | Caffeine (in-process, TTL + size cap)                            |
| AOP              | Spring AOP via `spring-boot-starter-aspectj`                     |
| Async            | `@Async` + `ApplicationEventPublisher` on a custom thread pool   |
| File storage     | Local disk by default; `AssetStore` interface lets S3 plug in    |
| Validation       | Jakarta Validation (`@NotBlank`, `@Email`, `@Size`)              |
| Tests            | (TODO — JUnit 5 + Mockito + Testcontainers)                      |

---

## Architecture

```
                 +-------------------------+
HTTP request --->|  Spring filter chain    |
                 |   JwtAuthenticationFilter (parses Bearer, sets SecurityContext)
                 |   UsernamePasswordAuthenticationFilter
                 +-----------+-------------+
                             |
                             v
                 +-------------------------+
                 |  Controller             |   <-- @PreAuthorize("@projectGuard.xxx(#id)")
                 +-----------+-------------+
                             |
                             v
                 +-------------------------+
                 |  Service                |   <-- @Transactional, @Cacheable, @Audited
                 |    - ProjectGuard checks (defense in depth)
                 |    - publishes ApplicationEvent
                 +-----------+-------------+
                             |
                             v
                 +-------------------------+
                 |  Repository (JpaRepo)   |
                 +-----------+-------------+
                             |
                             v
                 +-------------------------+
                 |  PostgreSQL             |
                 +-------------------------+

                 +-------------------------+
event publisher  |  @TransactionalEventListener(AFTER_COMMIT)
   ---------->   |  @Async (separate thread pool)
                 |  NotificationListener
                 +-------------------------+
```

### Package layout

```
com.wildme.wildbook_lite/
├── WildbookLiteApplication.java
├── auth/             User, Role, JWT, AuthController       (feature-based)
├── project/          Project, ProjectMember, ProjectGuard  (feature-based)
├── comment/          nested under encounters               (feature-based)
├── notification/     EncounterCreatedEvent + @Async listener (feature-based)
├── common/           BaseEntity, AuditAspect, ForbiddenException, CsvWriter
├── config/           SecurityConfig, JpaConfig, AsyncConfig, CacheConfig
├── controller/       (legacy layered) encounter, individual, observer, sighting, media, export
├── service/          (legacy layered)
├── repository/       (legacy layered)
├── dto/              (legacy layered)
├── entity/           (legacy layered, pre-BaseEntity)
├── exception/        GlobalExceptionHandler + domain exceptions
└── storage/          AssetStore + LocalAssetStore (Strategy pattern)
```

> Note: the project is mid-migration from a classical layered structure
> (`controller/`, `service/`, etc.) to feature-based packages
> (`auth/`, `project/`, `comment/`, ...). New features live in feature-based
> packages; older entities still sit under their layer directories. Both
> patterns are visible side-by-side intentionally — refactoring the legacy
> packages is on the to-do list.

---

## Quick start

### 1. Postgres

Make sure Postgres is running on `localhost:5432` and that the `wildbook`
database + user exist:

```bash
psql postgres <<'EOF'
CREATE USER wildbook WITH PASSWORD 'wildbook';
CREATE DATABASE wildbook OWNER wildbook;
GRANT ALL PRIVILEGES ON DATABASE wildbook TO wildbook;
EOF
```

A `docker-compose.yml` is provided as an alternative (run `docker compose up -d`).

### 2. Build and run

```bash
./mvnw spring-boot:run
```

The app listens on `http://localhost:8080` and creates the schema on first
boot via `spring.jpa.hibernate.ddl-auto=update`.

### 3. End-to-end smoke test

```bash
# Register and capture the JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"password123"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')

# Create a project — creator becomes OWNER automatically
PROJ=$(curl -s -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Hawaii Humpbacks 2026","description":"Winter season"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')

# Create an encounter (this publishes an async EncounterCreatedEvent)
curl -s -X POST http://localhost:8080/api/encounters \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"projectId\":$PROJ,\"location\":\"Maui\",\"species\":\"Humpback whale\"}"

# List encounters in the project (permission check via ProjectGuard)
curl -s "http://localhost:8080/api/encounters?projectId=$PROJ" \
  -H "Authorization: Bearer $TOKEN"

# Comment on an encounter
curl -s -X POST "http://localhost:8080/api/encounters/1/comments" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"body":"Confirmed sighting"}'

# Stream encounters as CSV (memory-flat, even at 10M rows)
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/encounters/export.csv?projectId=$PROJ" \
  -o encounters.csv

# Request without a token returns 401, with a stale token returns 401,
# without project membership returns 403.
```

---

## Key API endpoints

| Method | Path                                          | Auth                         |
| ------ | --------------------------------------------- | ---------------------------- |
| POST   | `/api/auth/register`                          | public                       |
| POST   | `/api/auth/login`                             | public                       |
| GET    | `/api/projects`                               | bearer (lists my projects)   |
| POST   | `/api/projects`                               | bearer                       |
| GET    | `/api/projects/{id}/members`                  | bearer + project read        |
| POST   | `/api/projects/{id}/members`                  | bearer + project manage      |
| GET    | `/api/encounters?projectId=...`               | bearer + project read        |
| POST   | `/api/encounters`                             | bearer + project write       |
| GET    | `/api/encounters/{id}` (cached)               | bearer + project read        |
| PATCH  | `/api/encounters/{id}`                        | bearer + project write       |
| DELETE | `/api/encounters/{id}`                        | bearer + project write       |
| GET    | `/api/encounters/export.csv?projectId=...`    | bearer + project read        |
| GET    | `/api/encounters/{id}/comments`               | bearer                       |
| POST   | `/api/encounters/{id}/comments`               | bearer                       |
| DELETE | `/api/encounters/{id}/comments/{commentId}`   | bearer + author              |
| POST   | `/api/encounters/{id}/media`                  | bearer + project write       |
| GET    | `/api/encounters/{id}/media`                  | bearer + project read        |

Errors come back in a unified envelope:
```json
{ "timestamp": "2026-06-08T15:00:00Z", "status": 403, "error": "No read access to project: 1" }
```

---

## Notable design decisions

| Decision                                            | Why                                                                                                                                                  |
| --------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ProjectMember` is a first-class entity, not a `@ManyToMany` join table | The relationship carries data (role + audit columns); `@ManyToMany` cannot model that cleanly.                                                       |
| `ProjectGuard` is a Spring bean exposed to SpEL via `@PreAuthorize("@projectGuard.canRead(#id)")` | Reusable, testable, and gives the cache layer somewhere to live later.                                                                                |
| Stateless JWT (no Redis session store)              | Horizontal scaling needs no shared session store; refresh-token flow can be added when token rotation matters.                                       |
| Custom `AppPrincipal` carries `userId`              | Saves a DB round-trip per request to look the id up by username.                                                                                     |
| `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` | Side effects (notifications, emails) only fire if the originating transaction commits, and they never block the HTTP response.                       |
| In-process Caffeine cache, not Redis                | Single-node deploys do not need a network hop. The interface is `CacheManager`, so swapping in Redis later is a configuration change, not a rewrite. |
| Streaming CSV via `StreamingResponseBody` + JPA `Stream` + `fetch_size` | Memory stays flat for 10M-row exports. Naturally back-pressured via TCP → OutputStream → Hibernate cursor.                                            |
| `BaseEntity` + `@MappedSuperclass`                  | Centralises `id`, `createdAt`, `updatedAt`, `version` on every new entity (audit + optimistic locking out-of-the-box).                              |

---

## Interview talking points

The features above map directly to common backend interview topics:

- **Spring core**: IOC, DI, bean lifecycle, AOP proxies (JDK vs CGLIB),
  `@Transactional` self-invocation pitfall, `@Async` self-invocation pitfall.
- **Spring Security**: filter chain order, stateless session, `UserDetailsService`,
  `@PreAuthorize` with SpEL bean references, defence in depth (annotation +
  service guard).
- **JWT**: structure (header.payload.signature), HS256 vs RS256, why short
  expiry + refresh token.
- **JPA / Hibernate**: `@MappedSuperclass`, `@Version` optimistic locking,
  `@TransactionalEventListener`, JPA `Stream` with `fetch_size` for cursor-based reads.
- **Caching**: cache-aside pattern, TTL, three classic problems (penetration,
  breakdown, avalanche), Caffeine vs Redis trade-off.
- **Concurrency**: `ThreadPoolExecutor` 7 parameters, 4 rejection policies, why
  not `Executors.newFixedThreadPool`.
- **AOP**: `@Around` advice, proxy mechanism, ordering, real use cases (audit,
  metrics, caching, transactions).
- **Database**: indexes (added via `@Index` on `Encounter.project_id`,
  `Comment.encounter_id`), HikariCP pool sizing, isolation levels (default
  `READ_COMMITTED`), Postgres FTS (planned next).
- **REST design**: nested sub-resources (`/encounters/{id}/comments`),
  unified error envelope, scope-based filtering, status-code semantics
  (401 vs 403 vs 404 vs 409).
- **Security at scale**: IDOR prevention (every endpoint validates resource
  ownership), BCrypt for passwords, generic error messages, no stack traces
  in responses.

---

## Roadmap

- [ ] Enable Flyway and stop relying on `ddl-auto=update`
- [ ] Refactor legacy entities (`Encounter`, `Individual`, `Observer`,
  `Sighting`, `MediaAsset`) to extend `BaseEntity`
- [ ] Postgres full-text search on encounters (`tsvector` + GIN index)
- [ ] Unit tests with Mockito + integration tests with Testcontainers
- [ ] OpenAPI/Swagger UI once `springdoc-openapi` supports Spring Boot 4
- [ ] Refresh token flow + token revocation list
- [ ] Async export job for very large datasets (S3 + signed-URL link)
- [ ] Refactor legacy layered packages into feature-based packages

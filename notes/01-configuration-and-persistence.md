# Unit 1 — Configuration & Persistence

Spring Boot 4.1 · Java 21 · PostgreSQL 18 · Maven

---

## 1. The persistence stack

Three separate layers that people wrongly collapse into "the database":

| Layer | What it is | Python equivalent |
|---|---|---|
| **JPA** (Jakarta Persistence API) | A *specification*. Interfaces only — no runnable code | — |
| **Hibernate** | The *implementation*. Maps objects ↔ rows, generates SQL | SQLAlchemy |
| **JDBC driver** | Speaks the database's wire protocol over TCP | psycopg2 |

`spring-boot-starter-data-jpa` bundles the spec + Hibernate. It deliberately includes **no driver**, because Hibernate is database-agnostic.

> Forgetting the driver → `Failed to determine a suitable driver class`

**Key idea:** the spec/implementation split means you can swap Hibernate for EclipseLink, or Postgres for MySQL, without rewriting business logic. That is the point of programming against interfaces.

---

## 2. Layered architecture

```
HTTP request
    ↓
CONTROLLER    speaks HTTP        URLs, status codes, JSON ↔ object
    ↓
SERVICE       speaks business    "reading out of range → create alert"
    ↓
REPOSITORY    speaks persistence find / save / delete
    ↓
DATABASE      speaks constraints NOT NULL, UNIQUE, FK
```

**The rule that gives layering its value:** each layer knows the layer below it and nothing about the layer above.

- A service returning `ResponseEntity` has leaked HTTP into the domain → can no longer be called from a scheduled job or CLI.
- A controller containing a query string has leaked SQL upward.

**Payoff:** each layer has *one reason to change*. Swap the database → only the repository moves. Swap REST for gRPC → only the controller moves.

An **entity is not a layer.** It's the data that travels *through* the layers.

### Python mapping

| Layer | Spring | Flask / FastAPI |
|---|---|---|
| Controller | `@RestController` | the `@app.route` function |
| Service | `@Service` | `services.py` |
| Repository | `JpaRepository` | SQLAlchemy session queries |
| Entity | `@Entity` | SQLAlchemy model |

### Package structure

**Package-by-feature** (chosen) beats package-by-layer:

```
patient/            NOT     controller/
  Patient                     PatientController
  PatientRepository           VitalController
  PatientService            service/
  PatientController           PatientService
vitals/                     repository/
  VitalReading                PatientRepository
```

**Why:** *information hiding*. In package-by-feature, `PatientRepository` can be **package-private** — so `AlertService` physically *cannot* bypass the service layer and hit the database directly. The compiler enforces the architecture. In package-by-layer everything must be `public` to be reachable across packages, so layering degrades into a convention people are asked to respect.

---

## 3. Validation

### The classifying question

> **Can I decide this without querying the database?**

| | Question it answers | DB needed? | Enforced at |
|---|---|---|---|
| **Input validation** | Is this well-formed? | No | Controller (`@Valid`) |
| **Business rule** | Is this allowed given current state? | Yes | Service |

### Worked examples

| Case | Type | Layer |
|---|---|---|
| `email = "notanemail"` | Input | Controller — `@Email` |
| `name = ""` | Input | Controller — `@NotBlank` |
| `dob` in year 2400 | Input | Controller — `@Past` |
| patient with that email already exists | Business rule | Service **and** database |
| `ward = "ICU"` but no such ward | Business rule | Service |

### Null vs empty vs blank — a real trap

| Annotation | Rejects `null` | Rejects `""` | Rejects `"   "` |
|---|---|---|---|
| `@NotNull` | ✅ | ❌ | ❌ |
| `@NotEmpty` | ✅ | ✅ | ❌ |
| `@NotBlank` | ✅ | ✅ | ✅ |

**A SQL `NOT NULL` constraint does NOT reject an empty string.** `""` is a valid non-null value. For human-entered text use `@NotBlank`.

*(Python has no equivalent trap — `if not name:` catches both. Java's null/empty distinction is sharper.)*

### Annotations don't fire by themselves

Constraints are *written on* the entity/DTO, but only **enforced when `@Valid` appears on the `@RequestBody` parameter**. Without `@Valid` they are decorative.

**Location ≠ enforcement point.**

### Defense in depth — the duplicate-email race

Service-layer uniqueness checking alone is insufficient:

```
Request A                      Request B
─────────                      ─────────
findByEmail("k@x.com") → none
                               findByEmail("k@x.com") → none
save(A)                        
                               save(B)          ← both inserted
```

This is a **time-of-check to time-of-use (TOCTOU) race**. No amount of Java-side checking fixes it — only a `UNIQUE` constraint can, because the database serialises writes.

**Division of responsibility:**
- The **constraint** is the correctness guarantee.
- The **service check** exists to return a friendly `409 Conflict` instead of a raw `DataIntegrityViolationException`.

Principle name: **defense in depth** — the same check at multiple layers, each catching what the others structurally cannot.

---

## 4. Schema management

Hibernate manages **tables inside** a database. It does **not** create the database — that's a server-level object.

```powershell
psql -U postgres -c "CREATE DATABASE vitals;"
```

### `spring.jpa.hibernate.ddl-auto`

| Value | Behaviour on startup |
|---|---|
| `none` | Hibernate touches nothing |
| `validate` | Compares entities to schema; **fails to start** on mismatch |
| `update` | Adds tables/columns to match entities; **never removes** |
| `create` | Drops and recreates the schema — all data lost |
| `create-drop` | Same as `create`, plus drops on shutdown |

**Dev → `update`. Production → `validate` + a migration tool.**

Why `update` is dangerous in production:
- Never drops or narrows → removed fields linger, real schema silently drifts from code
- Generates DDL implicitly → nobody reviewed the `ALTER TABLE` about to run on live data
- Moves *structure* only, not *data* → splitting `name` into `firstName`/`lastName` loses every name
- No version record, no rollback

The production answer: **Flyway** or **Liquibase** — numbered SQL files, committed to git, code-reviewed, applied in order. Same concept as **Alembic** or **Django migrations**.

---

## 5. Configuration & secrets

### Two servers, two protocols

```
Browser ──HTTP──▶ Spring Boot ──Postgres wire protocol──▶ PostgreSQL
                     :8080                                   :5432
```

`8080` is *your app's* HTTP port. `5432` is a separate program that does not speak HTTP. `jdbc:postgresql://` selects the driver.

**JDBC URL shape:** `jdbc:<vendor>://<host>:<port>/<database>`

### Working configuration

```properties
spring.application.name=vitals
spring.jpa.hibernate.ddl-auto=update

spring.datasource.url=jdbc:postgresql://localhost:5432/vitals
spring.datasource.username=postgres
spring.datasource.password=${DB_PASSWORD}
```

### `application.properties` is NOT "the environment"

| | What it is |
|---|---|
| `application.properties` | A **file** in the repo, read by Spring at startup |
| The environment | **In-memory** key–value pairs held by the OS, attached to a process, inherited by children |

`${DB_PASSWORD}` is a **reference**, not the secret. The file stays safe to commit.

### Spring's `Environment` — property source precedence

Confusingly, Spring calls its *merged* config an `Environment`. Highest priority wins:

| Priority | Source |
|---|---|
| 1 | Command-line arguments |
| 2 | **OS environment variables** |
| 3 | `application-{profile}.properties` |
| 4 | `application.properties` |
| 5 | Framework defaults |

OS env vars sit **above** the file, so they override it.

**Relaxed binding:** `SPRING_DATASOURCE_PASSWORD` maps automatically to `spring.datasource.password` — no placeholder needed. Standard in containers/CI, but invisible when reading the file.

### Secrets rules

- **Git history is permanent.** Adding a secret to `.gitignore` *after* committing does nothing — it's in every clone forever. It must never be committed **once**. Fixing it properly means rewriting history *and* rotating the credential.
- **Externalize secrets, not configuration.** A secret is something whose disclosure causes harm. Host, port, database name, username = configuration; leaving them visible **documents** what the app needs.
- Bots scrape public GitHub for credential patterns continuously. Leaked cloud keys are used within minutes.
- **Never use AWS root account keys.** They cannot be scoped or limited. Create an IAM user with least privilege.

### Fail fast

`${DB_PASSWORD}` with **no default** → app refuses to start if the variable is missing.
`${DB_PASSWORD:changeme}` → silently proceeds and fails confusingly later.

**Prefer the loud failure.** Same principle appeared three times in this unit:
1. Placeholder with no default
2. Removing H2 so there's nothing to silently fall back to
3. `HikariPool.checkFailFast` — opens one connection at startup so a bad credential kills the app immediately rather than on first user request

---

## 6. Maven

### Dependency scopes

| Scope | On classpath when |
|---|---|
| `compile` (default) | Always — compile, test, and production |
| `runtime` | Running, not compiling (e.g. JDBC drivers) |
| `test` | Tests only — **cannot** reach production |

Same idea as Python's `dev-requirements.txt`, but enforced by the build tool.

### H2 vs PostgreSQL

**H2 is not a cache.** A cache sits *in front of* a database holding hot data (Redis). H2 is a **complete substitute database** — different engine, different SQL dialect.

| Option | Trade-off |
|---|---|
| H2 at `test` scope | Fast, zero-setup tests — but **dialect drift**: tests pass on H2, code breaks on Postgres |
| Postgres everywhere | No drift; needs a running DB, slower, leaves state behind |
| **Testcontainers** | Real Postgres in Docker per test run, disposable — the mature answer |

**Decision here:** removed H2 entirely. In a clinical-alerting system, test against what you deploy. Bonus: with H2 off the classpath, a misconfigured datasource fails loudly instead of silently starting an in-memory database.

### Maven wrapper

`mvnw` / `mvnw.cmd` download and run a pinned Maven version. **No Maven install needed**, and everyone on the project builds with the same version.

### Spring Boot 4.x renamed the starters

| Boot 3 tutorials say | Boot 4 actually uses |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` | `-webmvc-test`, `-data-jpa-test`, `-validation-test` |

Also expect `javax.*` → `jakarta.*` in older material. **Spring docs are version-pinned in the URL — check the version selector before trusting a snippet.**

---

## 7. Java toolchain

### Package names vs artifact IDs

| | Rule | Reader |
|---|---|---|
| Java package | Valid **identifiers** — lowercase, dots, **no hyphens** (`-` is subtraction) | the compiler |
| Maven artifactId | kebab-case is conventional; hyphens fine | Maven |

Different rules because different readers. Also: **filename must match the public class name.**

### Target version ≠ runtime version

- `<java.version>21</java.version>` = what you **compile to**
- `JAVA_HOME` = the JDK Maven actually **uses**
- `java` on `PATH` = irrelevant to Maven

A compiler cannot target a version newer than itself:

> `error: release version 21 not supported` ← JAVA_HOME was JDK 17

### Environment variable scopes

**User-level overrides Machine-level.** When a variable "won't change", something narrower is shadowing it:

```powershell
[Environment]::GetEnvironmentVariable("JAVA_HOME","User")
[Environment]::GetEnvironmentVariable("JAVA_HOME","Machine")
```

**Processes snapshot the environment at launch.** Editing the registry only affects *future* processes. Children inherit from their parent — so a VSCode terminal carries VSCode's environment, and fixing it requires restarting **VSCode**, not just the terminal.

### Shell syntax

| | PowerShell | Bash |
|---|---|---|
| Set (session) | `$env:NAME = "x"` | `export NAME='x'` |
| Read | `$env:NAME` | `$NAME` |
| Run wrapper | `.\mvnw` | `./mvnw` |
| Persist (Windows) | `[Environment]::SetEnvironmentVariable("NAME","x","User")` | — |

Persisting at User level makes the variable visible to **both** shells, since Git Bash on Windows inherits the Windows environment.

---

## 8. Repository structure

```
java-crud-app/
  .gitignore          ← OS/editor junk only
  backend/
    .gitignore        ← target/, *.class
    pom.xml
    src/main/java/com/kbcoding/vitals/
  frontend/           (later)
    .gitignore        ← node_modules/, dist/
```

**Why siblings, not backend-at-root:** CI can filter on paths —

```yaml
on:
  push:
    paths: ['backend/**']
```

With the backend at the root, "did the backend change?" has no clean expression, so every commit rebuilds everything.

**Nested `.gitignore` files** each apply to their own subtree, so rules travel with the thing they describe. Same instinct as package-by-feature: keep knowledge next to what it's about.

---

## 9. Debugging methods

### Read stack traces bottom-up

Each `Caused by:` is a lower-level cause. **The last one is the root.**

```
BeanCreationException: entityManagerFactory       ← symptom
  Caused by: Unable to determine Dialect...       ← consequence ⚠ RED HERRING
    Caused by: FATAL: password authentication     ← ROOT CAUSE
               failed for user "postgres"
```

Searching the *middle* message leads to advice about setting `hibernate.dialect` — which fixes nothing. Hibernate couldn't detect the dialect because it never got a connection to interrogate.

> **The loudest message is rarely the root cause.**

### Isolate the variable

Two suspects, one symptom: is the password wrong, or is the environment wrong?

```powershell
$env:PGPASSWORD = $env:DB_PASSWORD
psql -U postgres -d vitals -c "SELECT current_user, current_database();"
```

Feeding the **same input through a different client** splits the suspects apart. psql succeeding proved the secret was fine → the problem was the environment.

### A changed error message is information

`failed for user "postgres"` → `failed for user "wzbot"` meant the username stopped arriving. `wzbot` is the OS account — the Postgres driver **falls back to the OS user when no username is supplied**.

**An empty property is not the same as an absent one, but both can fail identically.** Spring also **silently ignores unknown property keys** — a typo like `spring.datasource.dpcp2.username` produces no error at all.

> When an error changes, read exactly *what* changed. A different error is progress.

### Port conflicts

"Port 8080 was already in use" doesn't say *who*. Identify before killing:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
(Get-CimInstance Win32_Process -Filter "ProcessId = <PID>").CommandLine
```

### SQLSTATE codes

Standardised and portable. Class `28` = invalid authorization; `28P01` = invalid password.

---

## 10. Open Session In View (unresolved decision)

```
WARN: spring.jpa.open-in-view is enabled by default
```

OSIV keeps the Hibernate session open for the **entire HTTP request**, including JSON serialisation.

| | |
|---|---|
| **For** | Lazy relationships still work after the service returns — no `LazyInitializationException` |
| **Against** | Breaks layering (SQL fires from the web layer); causes **N+1 queries**; holds a DB connection for the whole request; hides bugs until a later refactor |

Setting `spring.jpa.open-in-view=false` makes lazy access outside a transaction throw immediately, at the boundary where the mistake was made. Costs: you must consciously decide what each service method loads.

Matters most once `VitalReading` gets its `@ManyToOne` to `Patient`.

---

## 11. Commands to remember

```powershell
# Create the database (Hibernate won't)
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -c "CREATE DATABASE vitals;"

# Persist an env var for the Windows user (restart terminal + VSCode after)
[Environment]::SetEnvironmentVariable("DB_PASSWORD", "value", "User")

# Build and run
cd backend
.\mvnw spring-boot:run          # Ctrl+C to stop
.\mvnw -q compile               # compile only
.\mvnw validate                 # parse the pom without compiling

# What's on a port
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

### A healthy startup log

```
Starting VitalsApplication using Java 21.0.12
HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@...
Database dialect: PostgreSQLDialect
Database version: 18.3
Initialized JPA EntityManagerFactory
Tomcat started on port 8080 (http)
Started VitalsApplication in 2.702 seconds
```

`PgConnection` is the line that proves you're on real Postgres and not something auto-configured behind your back.

---

## 12. Glossary

| Term | Meaning |
|---|---|
| **JPA** | Jakarta Persistence API — the specification |
| **Hibernate** | The ORM implementing JPA |
| **JDBC** | Java Database Connectivity — the low-level driver API |
| **HikariCP** | Spring Boot's default connection pool |
| **Dialect** | Hibernate's per-database SQL variant, auto-detected from the connection |
| **DDL** | Data Definition Language — `CREATE`/`ALTER` |
| **Bean** | An object Spring constructs and manages |
| **Starter** | A curated bundle of Maven dependencies |
| **OSIV** | Open Session In View |
| **TOCTOU** | Time-of-check to time-of-use (a race condition) |
| **SQLSTATE** | Standardised 5-char database error code |

---

## 13. Self-test

Answer without looking:

1. Why doesn't `spring-boot-starter-data-jpa` include a database driver?
2. Why can a service returning `ResponseEntity` be considered a design flaw?
3. Which is package-by-feature's real advantage — and what does the *compiler* have to do with it?
4. A patient's `name` arrives as `""`. Input validation or business rule? Which annotation, and why isn't `NOT NULL` enough?
5. You already check for a duplicate email in the service. Why is a `UNIQUE` constraint still required?
6. Why is `ddl-auto=update` acceptable in dev but dangerous in production? Name the tool that replaces it.
7. `application.properties` is committed and contains `${DB_PASSWORD}`. Why is that safe?
8. You committed a password, then removed it in the next commit. Are you safe? Why not?
9. `JAVA_HOME` is JDK 17, pom says `<java.version>21</java.version>`. What fails, and at which phase?
10. You edited `JAVA_HOME` in the registry but your terminal still shows the old value. Why?
11. A stack trace has three `Caused by:` clauses. Which do you read first?
12. Why does removing H2 from the classpath make misconfiguration *easier* to detect?

---

## Next unit

**Step 1 — the `Patient` entity.** Design questions to answer before writing code:

1. Java type for each field — especially `dob` (several date/time types exist; most are wrong)
2. `@Id` generation strategy: `IDENTITY` / `SEQUENCE` / `AUTO` / `TABLE` — which suits Postgres, and how does it affect batch inserts?
3. Which validation annotations, and which constraints belong on `@Column` as well (defense in depth)
4. What's missing from `id, name, dob, email, ward` that a real hospital record needs?

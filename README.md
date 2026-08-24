# Pistonworks Garage

An auto service garage management system built for the **SoftUni Spring Advanced** individual
project. Customers register their vehicles and book repairs; the workshop schedules the job,
logs the labour, and draws the parts it needs from a separate inventory service.

The solution is made of **two independent Spring Boot applications**, each with its own
database and its own port:

| Application | Role | Port | Database |
|---|---|---|---|
| `garage-app` | Main application — Spring MVC + Thymeleaf front end | 8080 | PostgreSQL `garage` |
| `parts-svc` | REST microservice — parts inventory and stock ledger | 8081 | PostgreSQL `parts` + MongoDB |

## Tech stack

- **Java 21**, **Spring Boot 3.5.3**, **Maven** (with wrapper)
- **Spring MVC** + **Thymeleaf** for the front end, **Spring Security** for authentication
- **Spring Data JPA** over **PostgreSQL** (a separate database per application)
- **Spring Data MongoDB** for the append-only stock ledger
- **Spring Cloud OpenFeign** for inter-service calls
- **Spring HATEOAS** for the REST API representations
- **Redis** via Spring Cache abstraction
- **Spring AOP** for cross-cutting monitoring
- **JJWT** for signed service-to-service tokens
- **OpenPDF** and **Apache POI** for document exports
- **Lombok**, **Bean Validation**
- **JUnit 5**, **Mockito**, **Spring Security Test**, **H2**, **JaCoCo**
- **Docker Compose** for the whole stack

## Architecture

The applications are organised **feature first** — each business feature owns its entity,
repository, service, DTOs and controller, rather than being split across technical layers.
Cross-cutting concerns live under `common/` (auditing, events, aspects, exceptions,
scheduling, validation) and framework wiring under `config/`.

```
garage-app/src/main/java/bg/softuni/garage
├── common/      auditing, domain events, AOP, exceptions, scheduled jobs, validators
├── config/      security, caching, scheduling, seed data
├── home/        public pages
├── mechanic/    workshop staff
├── parts/       Feign client to parts-svc + inventory screens
├── repairorder/ repair orders and service tasks
├── user/        accounts, roles, permissions, profile
├── vehicle/     customer vehicles
└── workshop/    the mechanics' board

parts-svc/src/main/java/bg/softuni/partssvc
├── common/      exceptions, events, AOP, scheduled jobs
├── config/      JWT security, caching, scheduling, seed data
├── ledger/      MongoDB stock ledger
├── part/        parts catalogue
├── reservation/ stock reservations
└── supplier/    suppliers
```

## Domain model

**garage-app** — `Vehicle`, `RepairOrder`, `ServiceTask`, `Mechanic` are the domain entities.
`User`, `Role`, `Permission` and `AuditEntry` are technical.

**parts-svc** — `Part`, `PartReservation` and `Supplier`, plus the `StockLedgerEntry`
MongoDB document.

Every entity uses a **UUID** primary key, and relationships are unidirectional `@ManyToOne`.

A repair order moves through `REQUESTED → SCHEDULED → IN_PROGRESS → COMPLETED`, or is
`CANCELLED` along the way. References are generated as `RO-<year>-<random>`, where the suffix is taken from a UUID so two simultaneous bookings can never collide on the unique reference column.

## Functionalities

Each of these is triggered by a user from the front end, hits a POST/PUT/DELETE endpoint,
changes state, and shows a visible result.

**Vehicles** — register a vehicle · edit its details · retire or reactivate it · delete it

**Repair orders** — book a repair · assign a mechanic and a slot · complete the order ·
cancel the order

**Service tasks** — log a task on an order · mark a task done · remove a task

**Parts** (these travel to the microservice over Feign) — reserve parts for a job ·
return reserved parts to stock · consume reserved parts when the job is completed ·
restock a part

**Administration** — manage mechanics · change a user's role · suspend or reactivate
an account · review the audit trail

**Documents** — download a completed repair order as a PDF invoice · export the parts
inventory as an Excel workbook

### Business rules worth knowing

- A vehicle may only have one open repair order at a time, and a retired vehicle cannot be booked in.
- Mileage may never be edited downwards.
- A job is only assignable to an **active** mechanic whose specialty **matches** the work required.
- Service tasks can only be logged once a mechanic is assigned; adding one moves the order into
  progress and recalculates the labour cost from the mechanic's hourly rate.
- An order cannot be completed while any task is still pending.
- Vehicles and mechanics that appear on past repair orders are retired or deactivated, never deleted.
- Customers only ever see their own vehicles and orders.

## Roles and permissions

Three roles, each carrying a set of fine-grained permissions that are enforced with
`@PreAuthorize("hasAuthority(...)")` alongside the URL rules.

| Role | Permissions |
|---|---|
| `CUSTOMER` | `VEHICLE_MANAGE`, `ORDER_BOOK` |
| `MECHANIC` | `ORDER_ASSIGN`, `ORDER_WORK`, `PART_RESERVE` |
| `ADMIN` | all nine, including `USER_MANAGE`, `MECHANIC_MANAGE`, `PART_RESTOCK` |

Endpoint access is a deliberate mix: `/`, `/about`, `/login` and `/register` are open;
`/vehicles`, `/orders` and `/profile` need any signed-in user; `/workshop` needs a mechanic
or admin; `/admin/**` needs an administrator. Passwords are hashed with BCrypt and CSRF
protection is enabled throughout.

### Demo accounts

Seeded automatically on first start.

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `mechanic` | `mechanic123` | MECHANIC |
| `customer` | `customer123` | CUSTOMER |

## Localisation

The interface ships in **English and Bulgarian**. The language is switched with the `EN`/`BG`
control in the header (`?lang=en` / `?lang=bg`) and remembered in a cookie, so it survives
navigation and sign-in. Translations live in `src/main/resources/i18n/`.

## Documents

A completed repair order can be downloaded as a **PDF invoice** listing the labour lines, the
parts actually fitted, and the totals — visible to the customer who owns the order and to
workshop staff. Administrators can export the whole parts catalogue as an **Excel workbook**,
with anything below its reorder level highlighted; that endpoint requires the `REPORT_EXPORT`
permission.

## Web pages

Home · About *(static)* · Login · Register · My vehicles · Vehicle form · My repair orders ·
Book a service · Repair order details · Workshop board · Parts catalogue · Inventory ·
Mechanics · Mechanic form · User management · Audit trail · My profile · Edit profile ·
Error · Access denied

## Integrations

### parts-svc REST API

The main application talks to the microservice through a **Feign client**. Every call carries
a short-lived **JWT** signed with a shared secret, whose `scope` claim mirrors the acting
user's permissions; the microservice validates the signature and issuer and authorises
accordingly.

| Method | Endpoint | Used by the main application for |
|---|---|---|
| `GET` | `/api/parts` | the catalogue and the parts picker |
| `GET` | `/api/parts/low-stock` | the inventory page and the low-stock job |
| `GET` | `/api/parts/{id}/ledger` | stock movement history |
| `GET` | `/api/reservations?repairOrderId=` | parts fitted to a job |
| `POST` | `/api/reservations` | reserving parts for a job |
| `PUT` | `/api/reservations/{id}/consume` | billing parts when a job completes |
| `DELETE` | `/api/reservations/{id}` | returning parts when a job is cancelled |
| `POST` | `/api/parts/{id}/restock` | restocking from the inventory page |

Responses follow REST conventions and carry **HATEOAS** links; a reservation only advertises
its `consume` and `release` links while it is still open. Errors are returned as RFC 7807
`ProblemDetail` documents, and a Feign error decoder turns them back into ordinary,
readable messages in the UI.

Stock is tracked as `available = quantityOnHand - quantityReserved`. Reserving moves
availability only; on-hand stock is not reduced until the parts are actually consumed.

**If the microservice is unavailable**, catalogue pages degrade to a friendly message rather
than failing, cancelling an order still succeeds (the reservations are expired later by a
scheduled job), and completing an order is refused with a clear explanation rather than being
left half-finished.

### Other integrations

- **PostgreSQL** — one database per application
- **MongoDB** — append-only stock ledger; a ledger outage is logged but never blocks a stock operation
- **Redis** — Spring Cache backing store for both applications

## Scheduling and caching

Both applications run a cron job and a non-cron job.

| Application | Trigger | Job |
|---|---|---|
| `garage-app` | cron `0 0 7 * * *` | flag scheduled orders that are past their slot |
| `garage-app` | cron `0 30 3 * * SUN` | purge audit entries older than 90 days |
| `garage-app` | fixed delay | report parts below their reorder level |
| `parts-svc` | cron `0 0 2 * * *` | automatically reorder depleted parts |
| `parts-svc` | fixed rate | expire reservations that were never consumed |

Caching is Redis-backed with per-cache TTLs: the parts catalogue and low-stock report in both
applications, and the active mechanic roster in the main application. Every operation that
changes stock or the roster evicts the affected caches.

## Validation, error handling and logging

Validation is applied on all three layers — Bean Validation annotations on DTOs, column
constraints and annotations on entities, and business rules in the service layer. Registration
uses a custom class-level `@PasswordsMatch` constraint.

The main application has a `@ControllerAdvice` with handlers for three custom exceptions and
six built-in ones, so no request can reach a white-label error page. The microservice has a
`@RestControllerAdvice` returning `ProblemDetail`, with four custom and four built-in handlers.

Every functionality logs through SLF4J, and an AOP aspect in each application times service
calls and reports rejected ones.

## Testing

```bash
cd garage-app && ./mvnw verify
cd parts-svc  && ./mvnw verify
```

Both builds enforce a **minimum of 70% line coverage** through JaCoCo, and both currently
exceed it comfortably. Reports are written to `target/site/jacoco/index.html`.

The suites cover unit tests (JUnit 5 + Mockito over the service layer), integration tests
(`@SpringBootTest` against H2 with real repositories), and API tests (MockMvc for the web
layer, and the full REST surface of the microservice including JWT authorisation).

## Running the project

### Everything in Docker

```bash
docker compose up -d --build
```

That starts both databases, Redis, MongoDB and both applications. The garage is then at
http://localhost:8080 and the parts API at http://localhost:8081.

### Databases in Docker, applications from Maven

```bash
docker compose up -d garage-db parts-db cache ledger-db

cd parts-svc  && ./mvnw spring-boot:run
cd garage-app && ./mvnw spring-boot:run
```

Every port and credential is overridable with environment variables — `SERVER_PORT`,
`GARAGE_DB_PORT`, `PARTS_DB_PORT`, `REDIS_PORT`, `MONGO_PORT`, `PARTS_SERVICE_URL`,
`SERVICE_TOKEN_SECRET` — so the stack can be moved off the default ports if they are already
in use. Compose reads them from a `.env` file if one is present.

Prerequisites: JDK 21 (a newer JDK also works — the build targets Java 21 bytecode) and Docker.

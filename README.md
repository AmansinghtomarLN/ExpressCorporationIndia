# Shree Mahavir Express Services — Courier Booking & Tracking Web App

A production-ready Spring Boot web application modeled on shreemahavircourier.com, built with:

- **Java 17 + Spring Boot 3.3**
- **Spring MVC + Thymeleaf** (server-rendered pages)
- **Spring JDBC (`JdbcTemplate`)** — plain JDBC, no JPA/Hibernate, as requested
- **MySQL 8** database
- **Spring Security** — login / signup, BCrypt password hashing, role-based access (`CUSTOMER`, `ADMIN`)
- Courier **booking + real-time tracking** with a status timeline

## Pages / Sections (matching the reference site)

- Home (hero, quick-track widget, services overview, stats, CTA)
- About Us
- Services (Domestic Standard/Express, International, Freight, etc.)
- Branch Network
- Contact Us (with a working contact form saved to DB)
- Track Shipment (public — no login required, also has a JSON API + AJAX widget)
- Login / Sign Up
- Customer Dashboard (view your shipments) + Book a Shipment
- Admin Console (`/admin`) — view all shipments, add tracking status updates

## 1. Prerequisites

- JDK 17+
- Maven 3.9+
- MySQL 8.x running locally (or reachable via network)

## 2. Create the database

```sql
CREATE DATABASE mahavir_courier CHARACTER SET utf8mb4;
```

The app creates all tables automatically on startup via `schema.sql` (safe/idempotent —
`CREATE TABLE IF NOT EXISTS`), and seeds an admin account + demo data via `data.sql`.

## 3. Configure the database connection

Either edit `src/main/resources/application.properties`, or (recommended) set environment
variables — the app reads these with sensible local defaults:

```bash
export DB_URL="jdbc:mysql://localhost:3306/mahavir_courier?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
export DB_USERNAME=root
export DB_PASSWORD=your_mysql_password
```

## 4. Run it

```bash
cd mahavir-courier
mvn spring-boot:run
```

Or build a jar and run it:

```bash
mvn clean package -DskipTests
java -jar target/mahavir-courier.jar
```

The app starts on **http://localhost:8080**

### Production run (required)

Deployments **must** activate the `prod` profile so schema/data scripts do not re-run on every restart
(`spring.sql.init.mode=never`):

```bash
export SPRING_PROFILES_ACTIVE=prod
# or:
java -jar target/mahavir-courier.jar --spring.profiles.active=prod
```

Without `prod`, the app uses local defaults (`spring.sql.init.mode=always`).

## 5. Default logins (seeded by `data.sql`)

| Role     | Email                                 | Password    |
|----------|----------------------------------------|-------------|
| Admin    | admin@shreemahavircourier.com          | Admin@123   |

Admin console: **http://localhost:8080/admin** — after logging in as admin, use the **Admin** link
in the header/nav (or open `/admin` directly).

Sign up for a normal **customer** account at `/signup` to book and track your own shipments.

## 6. Try the tracking feature immediately

A demo shipment is seeded so you can test tracking without booking anything first:

```
Tracking ID: MH1000000001
```

Enter it on the homepage widget or at `/track`.

## 7. Project structure

```
src/main/java/com/mahavircourier/
  config/SecurityConfig.java        Spring Security setup (login, signup, roles)
  controller/                       MVC controllers (Home, Auth, Tracking, Dashboard, Admin)
  dao/                               JdbcTemplate-based DAOs (no JPA)
  model/                             Plain Java domain objects (User, Shipment, TrackingEvent, Branch)
  dto/                                Form-backing objects with Bean Validation
  service/                           Business logic (booking, tracking, auth)
src/main/resources/
  schema.sql                         Table definitions (auto-run on startup)
  data.sql                           Seed data (admin user, branches, demo shipment)
  application.properties             Base config (env-var driven)
  application-prod.properties        Production overrides
  templates/                         Thymeleaf HTML pages
  static/css/style.css               Site styling
  static/js/script.js                AJAX quick-track widget
```

## 8. Production deployment notes

- **Always run with the `prod` profile in deployment.** Set `SPRING_PROFILES_ACTIVE=prod` (or
  `--spring.profiles.active=prod`). This turns off automatic schema/data init
  (`spring.sql.init.mode=never` via `application-prod.properties`) so restarts do not re-run
  `schema.sql` / `data.sql`.
- **Never commit real DB credentials.** Use environment variables or a secrets manager.
- For real deployments, run `schema.sql` once (e.g. via a migration tool such as Flyway or
  manually) rather than relying on `spring.sql.init` in the long run.
- **Change the seeded admin password immediately** in a real deployment — either update it via
  SQL with a freshly generated BCrypt hash, or add an admin "change password" screen.
- Put this behind a reverse proxy (Nginx) with HTTPS/TLS termination in production.
- The HikariCP pool size is tunable via `DB_POOL_MAX` / `DB_POOL_MIN_IDLE` env vars.
- Consider adding rate-limiting on `/login`, `/signup`, and `/api/track/**` at the proxy layer
  to protect against brute-force and scraping.

## 9. Extending the tracking system

`ShipmentService.addTrackingUpdate(...)` is the single place that both appends a new
`tracking_events` row and updates the shipment's current `status` — call this from the admin
UI (already wired up at `/admin/shipments/{id}`) or from a future warehouse-scanner integration,
SMS webhook, or partner carrier API.

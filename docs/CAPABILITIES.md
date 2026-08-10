# Express Corporation of India — Application Capabilities

Last updated: 10 Aug 2026

Use this document as the source of truth for what the app can do today, who can do it, and how the admin suite is organized.

---

## 1. Product overview

Web app for courier **booking**, **public tracking**, **customer account**, and a full **admin operations console**.

| Layer | Technology |
|-------|------------|
| Backend | Java 17, Spring Boot 3.3 |
| UI | Thymeleaf + CSS (`/css/style.css`) |
| Data access | Spring JDBC (`JdbcTemplate`) — no JPA |
| Security | Spring Security, BCrypt passwords, CSRF on forms |
| Database | MySQL 8 (`mahavir_courier`) |

**Brand name (UI):** Express Corporation of India (ECI)

---

## 2. Roles & access

| Role | Access |
|------|--------|
| Anonymous | Marketing pages, track, contact, login/signup |
| `CUSTOMER` | Dashboard, book shipment, view own shipments |
| `STAFF` | Same as admin console (`/admin/**`) |
| `ADMIN` | Full admin console (`/admin/**`) |

**Login behavior**
- `ADMIN` / `STAFF` → redirect to `/admin` (dashboard)
- `CUSTOMER` → redirect to `/dashboard`

**Default seeded admin**
- Email: `admin@shreemahavircourier.com`
- Password: `Admin@123`
- Change this before any real deployment.

---

## 3. Public / customer capabilities

### Public (no login)
- Home, About, Services, Branch Network, Contact Us
- Track shipment page (`/track`)
- JSON track API (`/api/track/{trackingId}`) — used by homepage quick-track widget
- Login / Sign Up

### Customer (logged in)
- My Account dashboard — list own shipments
- Book a shipment (gets tracking ID immediately)
- Track any public tracking ID
- Logout (CSRF-safe)

### Booking validation
- Required sender/receiver fields
- Indian mobile pattern `^[6-9]\d{9}$`
- Service type allowlist: `DOMESTIC_STANDARD` | `DOMESTIC_EXPRESS` | `INTERNATIONAL`
- Optional COD amount (used for invoice)

### Public track privacy
- Public track page shows **names + cities**, not full street addresses
- API still returns full shipment object (left as-is by product decision)

---

## 4. Admin suite — what is implemented

Admin UI lives under `/admin` with a shared admin nav:

Dashboard · Shipments · Reports · Monitoring · Users · Branches · Contacts · Rates · Invoices · Notifications · Audit

### 4.1 Dashboard (`/admin`)
- KPI cards:
  - Total shipments
  - Booked / In transit / Out for delivery / Delivered
  - Delayed (past expected delivery, not terminal)
  - Unread contact messages
  - Unpaid invoices
- Recent shipments table with Manage links

### 4.2 Shipments (`/admin/shipments`)
**List**
- Search by tracking ID, sender/receiver name, or phone
- Filter by status
- Pagination with user-selectable page size (5 / 10 / 25 / 50 / 100)
- Columns include status, expected delivery, freight, COD

**Create**
- Admin can create a shipment (`/admin/shipments/new`)
- Can set branch/hub/courier/COD at create time
- Freight calculated from rate cards when possible
- Invoice auto-created

**Detail / manage (`/admin/shipments/{id}`)**
- View full party details, route, service, expected delivery, freight, COD
- Edit shipment details
- Assign branch / hub / courier name & phone
- Cancel shipment (terminal)
- Add tracking update with **allowed next statuses only** (transition rules)
- Require location on update
- Edit / delete individual tracking events
- Print label (`/admin/shipments/{id}/label`)
- Success flash after updates; pages are no-cache

**Bulk / export**
- Export CSV: `/admin/shipments/export.csv`
- Bulk status update: `/admin/shipments/bulk`  
  CSV format: `trackingId,status,location,remarks`

### 4.3 Status workflow (enforced for admin updates)

```
BOOKED            → PICKED_UP, CANCELLED
PICKED_UP         → IN_TRANSIT, AT_HUB, CANCELLED, RTO
IN_TRANSIT        → AT_HUB, OUT_FOR_DELIVERY, RTO
AT_HUB            → IN_TRANSIT, OUT_FOR_DELIVERY
OUT_FOR_DELIVERY  → DELIVERED, RTO
DELIVERED / CANCELLED / RTO → (terminal — no further transitions)
```

On each successful status update:
- Tracking event is appended
- Shipment current status is updated
- Notification log entries are written (EMAIL + SMS simulation)
- Audit log entry is written

### 4.4 Users (`/admin/users`)
- Search users
- Create user with role `CUSTOMER` | `STAFF` | `ADMIN`
- Enable / disable account
- Change role
- Reset password
- Delete user (cannot delete yourself or the last ADMIN)

### 4.5 Branches (`/admin/branches`)
- Create / edit / delete branches
- Public Branch Network page reads the same data

### 4.6 Contacts (`/admin/contacts`)
- Inbox of contact-form submissions
- Unread count
- Mark as read
- Delete

### 4.7 Rates (`/admin/rates`)
- Rate cards by service type
- Fields: min/max weight, base rate, per-kg rate, active flag
- Used to calculate freight on booking / admin create
- Seeded defaults:
  - Domestic Standard: base 80 + 15/kg
  - Domestic Express: base 120 + 25/kg
  - International: base 450 + 80/kg

### 4.8 Invoices (`/admin/invoices`)
- Auto-created when a shipment is booked/created
- Amounts from freight + COD
- Status actions: **PAID**, **UNPAID**, **COD_COLLECTED**

### 4.9 Notifications (`/admin/notifications`)
- Delivery log for EMAIL/SMS with status **SENT / FAILED / SKIPPED**
- Driven by real Spring Mail + Twilio providers

### 4.10 Audit (`/admin/audit`)
- Append-only log of admin mutating actions
  (create/edit/cancel shipment, user changes, branch/rate changes, invoice status, etc.)
- Login success / failure / logout events (with IP)
- Scheduled report + monitor alert events
- Shows actor email, action, entity, details, timestamp

### 4.11 Reports (`/admin/reports`)
- Generate **Daily / Weekly / Monthly / Custom** reports
- Calculations include:
  - Booked / delivered / cancelled-RTO counts
  - Delivery rate %, cancel rate %
  - Freight + COD totals, gross booked value, avg revenue/shipment, avg weight
  - Invoice freight/COD created, collected revenue, open unpaid totals
  - Status / service / top-origin breakdowns
  - Up to 500 shipment detail lines
- **Scheduled emails** (08:00):
  - Daily → yesterday
  - Weekly → previous Mon–Sun (Mondays)
  - Monthly → previous month (1st)
- Recipient: `app.report.email` (default `amansinghtomar2209@gmail.com`)
- Manual email button on the reports page

### 4.12 Monitoring (`/admin/monitoring`)
- DB up/down, mail/SMS configuration readiness
- Delayed shipments, unpaid invoices, failed notifications, live pipeline counts
- Active alert list
- Auto alert email every 30 minutes when thresholds breached
- Manual “Run alert check now”
- Actuator: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` (admin-protected except health/info)

### 4.13 Real notifications
- Status changes send **real email** (to booking user’s email) and **real SMS** (Twilio → receiver phone)
- Results logged as SENT / FAILED / SKIPPED in Notifications admin screen

---

## 5. Database tables (current)

| Table | Purpose |
|-------|---------|
| `users` | Customers, staff, admins |
| `branches` | Branch network |
| `shipments` | Shipments + assignment/freight/COD fields |
| `tracking_events` | Timeline history |
| `contact_messages` | Contact form inbox (+ read status) |
| `rate_cards` | Pricing |
| `invoices` | Billing / COD collection state |
| `notifications` | Real email/SMS delivery log (SENT/FAILED/SKIPPED) |
| `audit_logs` | Admin action + login/logout audit trail |

**Extra shipment fields (ops):**
`assigned_branch_id`, `assigned_hub`, `courier_name`, `courier_phone`, `freight_charge`, `cod_amount`

---

## 6. Important URLs

| URL | Who | Purpose |
|-----|-----|---------|
| `/` | Public | Home + quick track |
| `/track` | Public | Tracking page |
| `/api/track/{id}` | Public | JSON tracking |
| `/book` | Customer | Book shipment |
| `/dashboard` | Customer | My shipments |
| `/admin` | Admin/Staff | Ops dashboard |
| `/admin/shipments` | Admin/Staff | Shipment console |
| `/admin/reports` | Admin/Staff | Daily/weekly/monthly reports + email |
| `/admin/monitoring` | Admin/Staff | Health, alerts, channel readiness |
| `/admin/users` | Admin/Staff | User management |
| `/admin/branches` | Admin/Staff | Branch CRUD |
| `/admin/contacts` | Admin/Staff | Contact inbox |
| `/admin/rates` | Admin/Staff | Rate cards |
| `/admin/invoices` | Admin/Staff | Invoices |
| `/admin/notifications` | Admin/Staff | Notify log |
| `/admin/audit` | Admin/Staff | Audit log |
| `/actuator/health` | Public | Liveness/readiness |
| `/actuator/metrics` | Admin/Staff | App metrics |
| `/actuator/prometheus` | Admin/Staff | Prometheus scrape |

---

## 7. Run & profiles

### Local
```bash
# DB
CREATE DATABASE mahavir_courier CHARACTER SET utf8mb4;

# App (defaults: root / rootroot, SQL init = always)
mvn spring-boot:run
# → http://localhost:8080
```

### Production (required)
```bash
export SPRING_PROFILES_ACTIVE=prod
java -jar target/mahavir-courier.jar
# or: --spring.profiles.active=prod
```

`prod` sets `spring.sql.init.mode=never` so schema/data scripts do not re-run on every restart.

Env overrides: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`, etc. (see `application.properties`).

### Email + SMS setup (required for real delivery)

**Gmail SMTP (reports + status emails + alerts):**
```bash
export MAIL_USERNAME="your-gmail@gmail.com"
export MAIL_PASSWORD="your-gmail-app-password"   # Google Account → App passwords
export MAIL_FROM="your-gmail@gmail.com"
export REPORT_EMAIL="amansinghtomar2209@gmail.com"
```

**Twilio SMS (status SMS to receiver phones):**
```bash
export TWILIO_ACCOUNT_SID="ACxxxxxxxx"
export TWILIO_AUTH_TOKEN="xxxxxxxx"
export TWILIO_FROM_NUMBER="+1xxxxxxxxxx"
```

Without these, the app still runs; notifications are logged as `FAILED`/`SKIPPED`, and report emails will fail until mail is configured.

### Schedules (Asia/system default JVM timezone)
| Job | Default cron | Meaning |
|-----|--------------|---------|
| Daily report | `0 0 8 * * *` | 08:00 every day (covers yesterday) |
| Weekly report | `0 0 8 * * MON` | 08:00 Mondays |
| Monthly report | `0 0 8 1 * *` | 08:00 on the 1st |
| Monitor alerts | `0 0/30 * * * *` | Every 30 minutes |

---

## 8. Demo data

| Item | Value |
|------|--------|
| Demo tracking ID | `MH1000000001` |
| Admin login | `admin@shreemahavircourier.com` / `Admin@123` |

---

## 9. Capability checklist (implemented)

### Core courier
- [x] Public tracking (page + API + homepage widget)
- [x] Customer signup/login/logout
- [x] Customer booking + dashboard
- [x] Admin shipment list/search/filter/pagination
- [x] Admin tracking updates
- [x] Expected delivery visible in admin

### Admin ops (full suite)
- [x] Admin dashboard KPIs
- [x] Admin create/edit/cancel shipment
- [x] Assign branch / hub / courier
- [x] Status transition rules + location validation
- [x] Edit/delete tracking events
- [x] Print label
- [x] CSV export + bulk status update
- [x] User management (create/roles/enable/reset password/delete)
...
- [x] Real email via Spring Mail / Gmail SMTP (always BCC ops inbox)
- [x] Branch CRUD
- [x] Contact inbox
- [x] Rate cards + freight calculation
- [x] Invoices + COD status
- [x] Notification log (simulated)
- [x] Audit log
- [x] Admin/Staff auto-redirect after login
- [x] Admin nav across all admin pages

### Intentionally deferred / future
- [ ] Slim public tracking API DTO (PII still exposed on API by decision)
- [ ] Automated test suite (`src/test` still minimal/empty)
- [ ] Favicon asset
- [ ] Flyway/Liquibase formal migrations (currently schema.sql + idempotent ALTERs)
- [ ] PDF/Excel report export (HTML email + on-screen report available)

### Reporting / monitoring / notifications (added)
- [x] Daily / weekly / monthly / custom operations reports (full volume + financial calcs)
- [x] Scheduled report emails to `app.report.email` (default: amansinghtomar2209@gmail.com)
- [x] Manual “Email this report now” from `/admin/reports`
- [x] Real email via Spring Mail / Gmail SMTP (always BCC `amansinghtomar2209@gmail.com`)
- [x] Real SMS via Twilio API (when credentials configured)
- [x] Notification log stores SENT / FAILED / SKIPPED from real providers
- [x] Actuator health/metrics/prometheus
- [x] Admin monitoring page + threshold alerts (email every 30 min)
- [x] Login success/failure/logout audit events

---

## 10. Quick “where is the code?”

| Concern | Location |
|---------|----------|
| Security / roles / login redirect | `config/SecurityConfig.java` |
| Admin controllers | `controller/admin/*` |
| Shipment business logic | `service/ShipmentService.java` |
| Status rules | `service/StatusTransitions.java` |
| Admin templates | `templates/admin/*` |
| Admin nav fragment | `templates/fragments/admin-nav.html` |
| Schema | `resources/schema.sql` |
| Seed data | `resources/data.sql` |

---

## 11. How to extend safely

1. Prefer adding admin screens under `/admin/...` with `hasAnyRole("ADMIN","STAFF")`.
2. Call `AuditService.log(...)` on every mutating admin action.
3. For status changes, go through `ShipmentService.addTrackingUpdate(...)` so transitions + notifications stay consistent.
4. Keep `prod` profile mandatory in deployment so SQL init stays off.

---

*This file should be updated whenever a major capability is added, removed, or intentionally deferred.*

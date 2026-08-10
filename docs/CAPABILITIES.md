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

Dashboard · Shipments · Users · Branches · Contacts · Rates · Invoices · Notifications · Audit

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
- In-app log of simulated EMAIL/SMS on status changes
- Not connected to real SMTP/SMS providers yet
- Useful as a hook point for future gateway integration

### 4.10 Audit (`/admin/audit`)
- Append-only log of admin mutating actions  
  (create/edit/cancel shipment, user changes, branch/rate changes, invoice status, etc.)
- Shows actor email, action, entity, details, timestamp

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
| `notifications` | Simulated notify log |
| `audit_logs` | Admin action audit trail |

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
| `/admin/users` | Admin/Staff | User management |
| `/admin/branches` | Admin/Staff | Branch CRUD |
| `/admin/contacts` | Admin/Staff | Contact inbox |
| `/admin/rates` | Admin/Staff | Rate cards |
| `/admin/invoices` | Admin/Staff | Invoices |
| `/admin/notifications` | Admin/Staff | Notify log |
| `/admin/audit` | Admin/Staff | Audit log |

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
- [x] User management (create/roles/enable/reset password)
- [x] Branch CRUD
- [x] Contact inbox
- [x] Rate cards + freight calculation
- [x] Invoices + COD status
- [x] Notification log (simulated)
- [x] Audit log
- [x] Admin/Staff auto-redirect after login
- [x] Admin nav across all admin pages

### Intentionally deferred / future
- [ ] Real email/SMS gateway (Twilio/SMTP) — log exists, provider not wired
- [ ] Slim public tracking API DTO (PII still exposed on API by decision)
- [ ] Automated test suite (`src/test` still minimal/empty)
- [ ] Favicon asset
- [ ] Flyway/Liquibase formal migrations (currently schema.sql + idempotent ALTERs)

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

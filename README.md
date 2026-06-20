# Trimly Backend

Multi-shop barber/salon booking platform — backend API.

Built with **Spring Boot 3 (Java 21, Maven)**, **PostgreSQL**, **Spring Security + JWT**, following a phased MVP build plan derived from the full Trimly product design doc.

> This backend covers the core booking → service → billing → analytics loop for a multi-shop platform. Larger features from the original product vision (AI growth tools, marketing automation, payments gateway, subscriptions, training/recruitment modules) are intentionally deferred — see [Scope & Roadmap](#scope--roadmap) below.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language / Build | Java 21, Maven |
| Framework | Spring Boot 3.3.4 |
| Database | PostgreSQL |
| ORM | Spring Data JPA (Hibernate) |
| Auth | Spring Security, JWT (JJWT 0.12.x) |
| Validation | Jakarta Bean Validation |
| Boilerplate reduction | Lombok |
| Image storage (planned) | Cloudinary |

Frontend (not yet built): React + TypeScript, Vite, Tailwind CSS, TanStack Query, Recharts — mobile-first responsive web app (not native).

---

## Architecture

Single Spring Boot monolith — **not** microservices. Every shop-scoped table carries a `shop_id` column; tenant isolation is enforced in the service/controller layer (via `ShopAccessService`), not via separate databases per shop or PostgreSQL RLS (a deliberate simplification from the original doc's RLS-based design).

```
Client (React, planned)
   │  HTTPS / REST + JWT
   ▼
Spring Security (JwtAuthFilter → SecurityContext)
   │
   ▼
Controllers → Services/validation → Spring Data JPA → PostgreSQL
```

### Package structure

```
com.trimly.backend
├── entity/        # JPA entities
├── enums/         # Role, ServiceCategory, BookingStatus, PaymentMode, PaymentStatus
├── repository/    # Spring Data JPA repositories
├── service/       # ShopAccessService (tenant isolation checks)
├── controller/    # REST controllers
├── security/      # JWT util, auth filter, UserDetails, security config
├── config/        # SecurityConfig, GlobalExceptionHandler
├── dto/           # Request/response DTOs, grouped by feature (auth, shop, service, booking, bill, dashboard)
└── exception/     # Custom exception types (ResourceNotFoundException, ShopAccessDeniedException)
```

---

## Entity Model (ERD summary)

| Entity | Key fields | Notes |
|---|---|---|
| **User** | id, name, email, phone, passwordHash, role | role: OWNER / STAFF / CUSTOMER |
| **Shop** | id, name, address, locality, ownerId | tenant root |
| **ShopStaff** | id, shopId, userId, roleInShop | join table; links a user to a shop |
| **ServiceItem** | id, shopId, category, name, price, estTimeMinutes, imageUrl | category: MALE / FEMALE / CHILDREN |
| **Booking** | id, shopId, customerId (nullable), staffId, guestName, guestPhone, bookingDate, timeSlot, status | guest fields used when staff books on behalf of a walk-in/phone customer |
| **BookingServiceItem** | id, bookingId, serviceId, priceAtBooking | join table; snapshots price at time of booking |
| **Bill** | id, shopId, bookingId, totalAmount, paymentMode, paymentStatus | 1:1 with Booking; only billable once COMPLETED |

Naming note: `ServiceItem`/`BookingServiceItem` are named to avoid colliding with Spring's `@Service` annotation (map to tables `services` / `booking_services`).

---

## What's Built So Far

### Phase 1 — Foundation
- Project skeleton (Maven, Java 21, Spring Boot 3.3.4)
- All 7 entities + 5 enums mapped from the ERD
- Repositories for every entity, with shop-scoped query methods
- Spring Security + JWT:
    - `CustomUserDetails` / `CustomUserDetailsService` — adapts `User` to Spring Security
    - `JwtUtil` — generates/validates tokens; embeds `userId`, `role`, and `shopIds` (list) as claims
    - `JwtAuthFilter` — authenticates each request from its Bearer token
    - `SecurityConfig` — stateless sessions, public `/api/auth/**`, everything else requires auth
- `GlobalExceptionHandler` — consistent JSON error responses (400/401/403/404/500), no raw stack traces leak to clients
- **Auth endpoints**: `POST /api/auth/register`, `POST /api/auth/login`
    - Self-registration blocked for `STAFF` role (staff must be added by an owner)
    - Same generic "Invalid email or password" message on bad email or bad password (prevents user enumeration)

### Phase 2 — Shop & Services
- `POST /api/shops` — create a shop; auto-enrolls the creator as `ShopStaff` ("Owner"); **re-issues a fresh JWT** with the new shop's ID included in `shopIds`
- Any authenticated user can create a shop (platform-wide `Role` is not a hard gate — `ShopStaff` membership is the real per-shop permission source of truth)
- `ShopAccessService.verifyShopAccess(userId, shopId)` — reusable tenant-isolation check, used by every shop-scoped write endpoint from here on
- **ServiceItem CRUD**: `POST/GET/PUT/DELETE /api/shops/{shopId}/services`, with optional `?category=` filter
    - Listing is open to any authenticated user (customers need to browse services); writes require shop access
    - Update/delete double-check the service actually belongs to the `shopId` in the URL (not just that *a* service with that ID exists)

### Phase 3 — Booking
- `POST /api/shops/{shopId}/bookings` — create a booking:
    - If the requester is shop staff → guest booking (`guestName`/`guestPhone` required, `customerId` null)
    - Otherwise → customer books for themselves (`customerId` = their own ID)
    - Validates `staffId` actually belongs to the shop
    - **Slot-conflict check**: same staff + date + time slot blocked if an existing booking there is `PENDING`, `ACCEPTED`, or `COMPLETED` (only `REJECTED`/`CANCELLED` free the slot)
    - Validates all `serviceIds` exist and belong to this shop
    - Snapshots each service's current price into `BookingServiceItem.priceAtBooking`
- `BookingStatus.canTransitionTo(...)` — enum-level state machine:
    - `PENDING` → `ACCEPTED` / `REJECTED` / `CANCELLED`
    - `ACCEPTED` → `COMPLETED` / `CANCELLED`
    - `REJECTED`, `COMPLETED`, `CANCELLED` → terminal
- `PATCH /api/shops/{shopId}/bookings/{bookingId}/status` — staff-only; enforces the transition rules above
- `GET /api/shops/{shopId}/bookings` — shop/staff view, optional `?date=` and `?status=` filters
- `GET /api/customers/me/bookings` — a customer's own bookings across any shop (identity always derived from JWT, never a path param)

### Phase 4 — Billing & Dashboard
- `POST /api/shops/{shopId}/bookings/{bookingId}/bill`:
    - Only allowed once a booking is `COMPLETED`
    - Blocks double-billing the same booking
    - Recalculates total from `BookingServiceItem` server-side (never trusts a client-sent amount)
    - `paymentStatus` set to `PAID` immediately (no real payment gateway integrated yet — see Roadmap)
- `GET /api/shops/{shopId}/dashboard/summary?startDate=&endDate=`:
    - Total revenue (sum of bills in range)
    - Total bookings in range
    - Daily revenue breakdown (for bar-chart rendering)
    - Top customers by spend (resolves registered customers by name via `User`, guests by `guestName`)

---

## Security Notes

- Passwords hashed with BCrypt; never stored or returned in plaintext
- JWTs are stateless (no server-side session); signed with HMAC-SHA, secret via `JWT_SECRET` env var
- `shop_id` is **never** trusted from client-controlled fields for tenant-scoped writes — always derived from the authenticated user's `ShopStaff` membership
- Ownership double-checks on update/delete endpoints (e.g. a service's `shopId` matching the URL's `shopId`) prevent cross-tenant ID-guessing attacks
- `.gitignore` excludes `application.properties` (contains local DB credentials + JWT secret); `application.properties.example` should be used as the template for setup

---

## Known Loose Ends / Tech Debt

1. **Code duplication**: the Booking → DTO mapping logic (services list + total calculation) is duplicated across `BookingController` and `CustomerBookingController`. Should be extracted into a shared mapper or `BookingService` class.
2. **No server-side logging** in `GlobalExceptionHandler`'s catch-all — unexpected 500s aren't currently logged anywhere for debugging.
3. **`ddl-auto: update`** — convenient for active development, but should move to a proper migration tool (Flyway or Liquibase) before any real/production deployment.
4. **Git history** contains an old commit with a real local DB password and JWT secret (before `.gitignore` was corrected). Low risk on a private repo; should be scrubbed (`git filter-repo` / BFG) or secrets rotated before the repo ever goes public.
5. **Dashboard performance**: top-customer aggregation does some in-memory (Java stream) grouping and per-booking `User` lookups rather than pure SQL aggregation. Fine at current scale; would need optimization if a shop accumulates thousands of bills.
6. **Slot duration**: `Booking.timeSlot` is a single point in time with no duration — a 60-minute service doesn't block the next slot. Matches the wireframe's discrete time-slot grid for now; real duration-aware scheduling is a future enhancement.

---

## Scope & Roadmap

The original Trimly product design doc describes a much larger platform (full multi-tenant SaaS with PostgreSQL RLS, AI growth suggestions, marketing automation, subscription tiers, GST invoicing, barber training/recruitment marketplace, multi-country payments). That doc is treated as the **long-term vision**, not the literal build target — what's built here is a deliberately smaller, working MVP covering the doc's core P0 booking/service/billing/analytics features.

### Not yet built (known gaps vs. the product doc's MVP list)
- Real payment gateway integration (Razorpay/UPI) — currently `Bill.paymentMode` just *records* how payment was made; no actual transaction processing
- Push notifications / reminders
- Customer-facing profile/style-history view (data exists via `customerId`, no dedicated endpoint yet)
- Walk-in queue (FCFS) — explicitly deferred to v2 from the start of planning
- Ratings & reviews, loyalty points
- Employee performance dashboard (revenue/bookings per staff member) — buildable with existing data, intentionally scoped out of Phase 4 pending a fuller "performance" definition
- Expenses, profit, inventory tracking — require new entities not in the current ERD

### Larger, separate efforts (future phases, post-traction)
- AI-powered marketing suggestions (LLM API integration)
- Barber training modules (mini-LMS)
- Barber recruitment/marketplace portal
- Subscription billing for shop owners (Free/Pro/Business tiers)
- Multi-country payments, GST-compliant invoicing, white-label branding per shop

### Frontend
Not started. Planned: React + TypeScript, mobile-first responsive web app (not native — see earlier platform decision), Tailwind CSS, TanStack Query for data fetching, Recharts for the dashboard charts.

### Infra (planned, not yet set up)
- Docker / docker-compose for local Spring Boot + Postgres parity
- Deployment: Railway or Render (backend + Postgres), Vercel or Netlify (frontend)

---

## Local Setup

Requires Java 21 and a local PostgreSQL instance.

```bash
# create the database
createdb trimly

# copy the example config and fill in real values
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Set the following (either in `application.properties` or as environment variables):

```properties
DB_URL=jdbc:postgresql://localhost:5432/trimly
DB_USERNAME=postgres
DB_PASSWORD=your_password
JWT_SECRET=a_long_random_secret_string
```

Run:
```bash
mvn spring-boot:run
```

With `ddl-auto=update`, Hibernate creates all 7 tables automatically on first run.

### Quick API smoke test

```bash
# Register an owner
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Owner","email":"owner@example.com","phone":"9999999999","password":"password123","role":"OWNER"}'

# Use the returned token to create a shop
curl -X POST http://localhost:8080/api/shops \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"My Shop","address":"123 Main St","locality":"Pune"}'
```

---

## API Endpoint Summary

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | Public | No self-registration as STAFF |
| POST | `/api/auth/login` | Public | |
| POST | `/api/shops` | Authenticated | Re-issues JWT with new shopId |
| POST | `/api/shops/{shopId}/services` | Shop staff | |
| GET | `/api/shops/{shopId}/services` | Authenticated | `?category=` optional |
| PUT | `/api/shops/{shopId}/services/{serviceId}` | Shop staff | |
| DELETE | `/api/shops/{shopId}/services/{serviceId}` | Shop staff | |
| POST | `/api/shops/{shopId}/bookings` | Authenticated | Customer or staff (guest) booking |
| GET | `/api/shops/{shopId}/bookings` | Shop staff | `?date=`, `?status=` optional |
| PATCH | `/api/shops/{shopId}/bookings/{bookingId}/status` | Shop staff | Enforces valid state transitions |
| GET | `/api/customers/me/bookings` | Authenticated | Own bookings only |
| POST | `/api/shops/{shopId}/bookings/{bookingId}/bill` | Shop staff | Booking must be COMPLETED |
| GET | `/api/shops/{shopId}/dashboard/summary` | Shop staff | `?startDate=&endDate=` required |

---


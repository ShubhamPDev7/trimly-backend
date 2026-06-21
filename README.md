# Trimly Backend

Multi-shop barber/salon booking platform — backend API.

Built with **Spring Boot 3 (Java 21, Maven)**, **PostgreSQL**, **Spring Security + JWT**.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language / Build | Java 21, Maven |
| Framework | Spring Boot 3.3.4 |
| Database | PostgreSQL |
| Migrations | Flyway |
| ORM | Spring Data JPA (Hibernate) |
| Auth | Spring Security, JWT (JJWT 0.12.x) |
| Validation | Jakarta Bean Validation |
| Boilerplate reduction | Lombok |
| Testing | JUnit 5, Mockito, AssertJ |
| Image storage (planned) | Cloudinary |

Frontend (not yet built): React + TypeScript, Vite, Tailwind CSS, TanStack Query, Recharts — mobile-first responsive web app.

---

## Architecture

Single Spring Boot monolith. Every shop-scoped table carries a `shop_id` column; tenant isolation is enforced in the service layer via `ShopAccessService`.

```
Client (React, planned)
   │  HTTPS / REST + JWT
   ▼
Spring Security (JwtAuthFilter → SecurityContext)
   │
   ▼
Controllers (thin) → Services (business logic) → Spring Data JPA → PostgreSQL
```

### Package structure

```
com.trimly.backend
├── entity/        # JPA entities
├── enums/         # Role, ServiceCategory, BookingStatus, PaymentMode, PaymentStatus
├── repository/    # Spring Data JPA repositories
├── service/       # Business logic + BookingMapper + ShopAccessService
├── controller/    # Thin REST controllers
├── security/      # JWT util, auth filter, UserDetails, security config
├── config/        # SecurityConfig, GlobalExceptionHandler
├── dto/           # Request/response DTOs grouped by feature
└── exception/     # ResourceNotFoundException, ShopAccessDeniedException
```

---

## Entity Model

| Entity | Key fields | Notes |
|---|---|---|
| **User** | id, name, email, phone, passwordHash, role | role: OWNER / STAFF / CUSTOMER |
| **Shop** | id, name, address, locality, ownerId | tenant root |
| **ShopStaff** | id, shopId, userId, roleInShop | links a user to a shop |
| **ServiceItem** | id, shopId, category, name, price, estTimeMinutes, imageUrl | category: MALE / FEMALE / CHILDREN |
| **Booking** | id, shopId, customerId (nullable), staffId, guestName, guestPhone, bookingDate, timeSlot, status | guest fields used when staff books on behalf of a walk-in |
| **BookingServiceItem** | id, bookingId, serviceId, priceAtBooking | snapshots price at time of booking |
| **Bill** | id, shopId, bookingId, totalAmount, paymentMode, paymentStatus | 1:1 with Booking; only billable once COMPLETED |

---

## What's Built

### Phase 1 — Foundation
- Project skeleton (Maven, Java 21, Spring Boot 3.3.4)
- All 7 entities + 5 enums
- Repositories with shop-scoped query methods
- Spring Security + JWT:
  - `CustomUserDetails` / `CustomUserDetailsService`
  - `JwtUtil` — generates/validates tokens; embeds `userId`, `role`, `shopIds` as claims
  - `JwtAuthFilter` — authenticates each request from its Bearer token
  - `SecurityConfig` — stateless sessions, public `/api/auth/**`
- `GlobalExceptionHandler` — consistent JSON error responses, no stack traces exposed
- **Auth**: `POST /api/auth/register`, `POST /api/auth/login`
  - Self-registration blocked for `STAFF` role
  - Generic error message on bad credentials (prevents user enumeration)

### Phase 2 — Shop & Services
- `POST /api/shops` — creates shop, auto-enrolls creator as `ShopStaff` ("Owner"), re-issues JWT with new shopId
- `ShopAccessService.verifyShopAccess(userId, shopId)` — tenant-isolation check used by all shop-scoped writes
- **ServiceItem CRUD**: `POST/GET/PUT/DELETE /api/shops/{shopId}/services`
  - Optional `?category=` filter on listing
  - Update/delete verify the service belongs to the correct shop

### Phase 3 — Booking
- `POST /api/shops/{shopId}/bookings`:
  - Staff → guest booking (`guestName`/`guestPhone` required)
  - Customer → books for themselves (`customerId` = their own ID)
  - Validates `staffId` belongs to the shop
  - **Slot-conflict check**: same staff + date + timeslot blocked if status is `PENDING`, `ACCEPTED`, or `COMPLETED`
  - Validates all `serviceIds` belong to this shop
  - Snapshots each service price into `BookingServiceItem.priceAtBooking`
- `BookingStatus.canTransitionTo(...)` — enum-level state machine:
  - `PENDING` → `ACCEPTED` / `REJECTED` / `CANCELLED`
  - `ACCEPTED` → `COMPLETED` / `CANCELLED`
  - `REJECTED`, `COMPLETED`, `CANCELLED` → terminal
- `PATCH /api/shops/{shopId}/bookings/{bookingId}/status` — enforces transition rules
- `GET /api/shops/{shopId}/bookings` — optional `?date=` and `?status=` filters
- `GET /api/customers/me/bookings` — customer's own bookings; identity from JWT, never a path param

### Phase 4 — Billing & Dashboard
- `POST /api/shops/{shopId}/bookings/{bookingId}/bill`:
  - Only allowed on `COMPLETED` bookings
  - Blocks double-billing
  - Total recalculated server-side from `BookingServiceItem` (never trusts client-sent amount)
- `GET /api/shops/{shopId}/dashboard/summary?startDate=&endDate=`:
  - Total revenue, total bookings, daily revenue breakdown, top customers by spend
- `GET /api/shops/{shopId}/dashboard/staff-performance?startDate=&endDate=`:
  - Per-staff completed bookings and revenue, sorted by revenue descending

### Phase 5 — Service Layer & Customer Endpoints
- Extracted full service layer: `BookingService`, `ShopService`, `ServiceItemService`, `DashboardService`, `CustomerService`
- All controllers are thin — extract userId, delegate to service, return response
- Customer endpoints: `GET /PUT /DELETE /api/customers/me` — profile view, update, soft-delete

### Phase 6 — Testing
- **46 unit tests** across the full service layer using JUnit 5 + Mockito
- **90% line coverage, 100% class coverage** across all 7 service classes
- Covers: slot conflict detection, invalid status transitions, double-billing prevention, cross-tenant access, soft-delete guards, staff ownership checks, dashboard aggregation, BookingMapper price totals

---

## Security Notes

- Passwords hashed with BCrypt
- JWTs stateless, signed with HMAC-SHA, secret via `JWT_SECRET` env var
- `shop_id` never trusted from client input for writes — always derived from `ShopStaff` membership
- Update/delete endpoints verify resource belongs to the correct shop (prevents cross-tenant ID guessing)
- `application.properties` and `target/` excluded from git; `.example` file committed as setup template
- JWT signing secret rotated after original commit history exposure

---

## Known Tech Debt

1. ~~Booking → DTO mapping duplicated across controllers~~ — **Fixed.** Extracted into `BookingMapper`.
2. ~~No DB migrations~~ — **Fixed.** Flyway added (V1–V5).
3. ~~Any staff could add other staff~~ — **Fixed.** Restricted to shop owner via `verifyShopOwner()`.
4. ~~Top-customer aggregation grouped by name string~~ — **Fixed.** Now keys on `customerId`/`guestPhone`.
5. ~~Case-sensitive email matching~~ — **Fixed.** Email normalized to lowercase everywhere.
6. **Booking slot-conflict race condition** (TOCTOU) — concurrent requests can both pass the check before either saves. No DB-level unique constraint yet.
7. **Slot duration** — `Booking.timeSlot` is a point in time with no end time/duration.
8. **No remove-staff endpoint** — staff can be added but not removed.
9. `ShopStaff.roleInShop` is a free-text string — owner check does exact match on `"Owner"`. Should be an enum.
10. **Dashboard top-customer aggregation** — in-memory grouping with per-booking `User` lookups instead of SQL aggregation. Fine at current scale.

---

## Local Setup

Requires Java 21 and PostgreSQL.

```bash
createdb trimly
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Fill in:

```properties
DB_URL=jdbc:postgresql://localhost:5432/trimly
DB_USERNAME=postgres
DB_PASSWORD=your_password
JWT_SECRET=a_long_random_secret_string
JWT_EXPIRATION_MS=86400000
```

Run:

```bash
mvn spring-boot:run
```

Flyway applies all migrations automatically on startup.

### Quick smoke test

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Owner","email":"owner@example.com","phone":"9999999999","password":"password123","role":"OWNER"}'

# Create a shop (use token from above)
curl -X POST http://localhost:8080/api/shops \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"My Shop","address":"123 Main St","locality":"Pune"}'
```

---

## API Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | Public | STAFF self-registration blocked |
| POST | `/api/auth/login` | Public | |
| POST | `/api/shops` | Authenticated | Re-issues JWT with new shopId |
| POST | `/api/shops/{shopId}/staff` | Shop owner | |
| GET | `/api/shops/{shopId}/staff` | Shop staff | |
| DELETE | `/api/shops/{shopId}` | Shop owner | Soft delete |
| POST | `/api/shops/{shopId}/services` | Shop staff | |
| GET | `/api/shops/{shopId}/services` | Authenticated | `?category=` optional |
| PUT | `/api/shops/{shopId}/services/{serviceId}` | Shop staff | |
| DELETE | `/api/shops/{shopId}/services/{serviceId}` | Shop staff | |
| POST | `/api/shops/{shopId}/bookings` | Authenticated | Customer or staff (guest) booking |
| GET | `/api/shops/{shopId}/bookings` | Shop staff | `?date=`, `?status=` optional |
| PATCH | `/api/shops/{shopId}/bookings/{bookingId}/status` | Shop staff | Enforces state machine |
| POST | `/api/shops/{shopId}/bookings/{bookingId}/bill` | Shop staff | Booking must be COMPLETED |
| GET | `/api/shops/{shopId}/dashboard/summary` | Shop staff | `?startDate=&endDate=` required |
| GET | `/api/shops/{shopId}/dashboard/staff-performance` | Shop staff | `?startDate=&endDate=` required |
| GET | `/api/customers/me` | Authenticated | Own profile |
| PUT | `/api/customers/me` | Authenticated | Update name/phone |
| DELETE | `/api/customers/me` | Authenticated | Soft-delete account |
| GET | `/api/customers/me/bookings` | Authenticated | Own bookings only |
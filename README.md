# Trimly Backend

Multi-shop barber/salon booking platform — backend API.

Built with **Spring Boot 3 (Java 21, Maven)**, **PostgreSQL**, **Redis**, **Spring Security + JWT**, emails via **Resend**.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language / Build | Java 21, Maven |
| Framework | Spring Boot 3.3.4 |
| Database | PostgreSQL |
| Cache / Rate limiting | Redis |
| Migrations | Flyway (V1–V13) |
| ORM | Spring Data JPA (Hibernate) |
| Auth | Spring Security, JWT (JJWT 0.12.x) |
| Email | Resend (via REST) |
| Validation | Jakarta Bean Validation |
| Boilerplate reduction | Lombok |
| Testing | JUnit 5, Mockito, AssertJ |

Frontend (not yet built): React + TypeScript, Vite, Tailwind CSS, TanStack Query — mobile-first responsive web app.

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
                                                               → Redis (rate limiting)
                                                               → Resend (email)
```

### Package structure

```
com.trimly.backend
├── entity/        # JPA entities
├── enums/         # Role, ServiceCategory, BookingStatus, WalkInStatus, PaymentMode, PaymentStatus
├── repository/    # Spring Data JPA repositories
├── service/       # Business logic + BookingMapper + ShopAccessService
├── controller/    # Thin REST controllers
├── security/      # JWT util, auth filter, UserDetails, SecurityConfig
├── config/        # WebConfig, GlobalExceptionHandler
├── interceptor/   # RateLimitInterceptor (Redis-backed)
├── dto/           # Request/response DTOs grouped by feature
└── exception/     # ResourceNotFoundException, ShopAccessDeniedException
```

---

## Entity Model

| Entity | Key fields | Notes |
|---|---|---|
| **User** | id, name, email, phone, passwordHash, role | role: OWNER / STAFF / CUSTOMER |
| **Shop** | id, name, address, locality, timezone, ownerId | tenant root |
| **ShopStaff** | id, shopId, userId, roleInShop | links a user to a shop |
| **ShopHours** | id, shopId, dayOfWeek, openTime, closeTime, isOpen | per-day hours |
| **ShopClosedDate** | id, shopId, date, reason | one-off closures |
| **ServiceItem** | id, shopId, category, name, price, estTimeMinutes | soft-deletable |
| **Booking** | id, shopId, customerId (nullable), staffId, guestName, guestPhone, bookingDate, timeSlot, status | guest fields for walk-in bookings by staff |
| **BookingServiceItem** | id, bookingId, serviceId, priceAtBooking | snapshots price at booking time |
| **Bill** | id, shopId, bookingId (nullable), walkInQueueEntryId (nullable), totalAmount, paymentMode, paymentStatus | covers both booking and walk-in billing |
| **WalkInQueueEntry** | id, shopId, customerId (nullable), guestName, guestPhone, preferredStaffId, status | status: WAITING / IN_SERVICE / COMPLETED / CANCELLED / NO_SHOW |
| **WalkInQueueServiceItem** | id, queueEntryId, serviceId, priceAtJoin | snapshots price at queue join |
| **Review** | id, shopId, reviewerId, bookingId (nullable), walkInQueueEntryId (nullable), rating, comment, ownerReply, ownerRepliedAt | one review per booking or walk-in |
| **LoyaltyAccount** | id, shopId, customerId, balance | per-customer per-shop points balance |
| **LoyaltyTransaction** | id, shopId, customerId, billId, type (EARN/REDEEM), points, balanceAfter | full ledger |
| **RefreshToken** | id, userId, token, expiresAt | persisted refresh tokens with reuse detection |
| **PasswordResetToken** | id, userId, token, expiresAt | forgot-password flow |

---

## What's Built

### Auth
- `POST /api/v1/auth/register` — STAFF self-registration blocked; generic error on bad credentials
- `POST /api/v1/auth/login` — returns short-lived access token + refresh token
- `POST /api/v1/auth/refresh` — rotates refresh token (reuse detection: old token invalidated on use)
- `POST /api/v1/auth/forgot-password` — sends reset link via Resend
- `POST /api/v1/auth/reset-password` — validates token, updates password
- Rate limiting via Redis on login, register, forgot-password

### Shop Management
- Shop CRUD with soft delete
- Staff add / remove (owner only)
- Update shop profile (name, address, locality, timezone)
- Shop hours per day of week + one-off closed dates
- `ShopAccessService` — tenant isolation enforced on every shop-scoped write

### Services
- Full CRUD for service items (category: MALE / FEMALE / CHILDREN)
- Soft delete, optional `?category=` filter on listing

### Bookings
- Staff → guest booking (`guestName` / `guestPhone` required)
- Customer → self-booking (`customerId` = JWT identity)
- Slot-conflict detection (same staff + date + timeslot blocked unless REJECTED/CANCELLED)
- Enum-level state machine: `PENDING → ACCEPTED / REJECTED`, `ACCEPTED → COMPLETED / CANCELLED`
- Customer can cancel their own PENDING or ACCEPTED booking
- Email notifications: confirmation to customer + owner on create; status updates to customer on accept/reject; cancellation to both on cancel

### Billing
- Booking billing: only on COMPLETED bookings, double-bill prevented, total recalculated server-side
- Walk-in billing: same protection, tied to WalkInQueueEntry
- Loyalty points processed at bill creation (see below)

### Walk-in Queue
- Customer joins queue self-service or staff adds a guest
- Predictive wait time algorithm based on shop hours + service durations + queue position
- Statuses: WAITING → IN_SERVICE → COMPLETED / CANCELLED / NO_SHOW
- Email confirmation to customer on queue join (position + estimated wait)

### Loyalty Points
- **Earn**: 1 point per ₹10 spent (on finalAmount after any discount), awarded on every bill
- **Redeem**: `redeemPoints: true` in bill request — applies all available points as discount (1 point = ₹1 off), capped at 50% of bill value
- Per-customer per-shop balance in `loyalty_accounts`; full ledger in `loyalty_transactions`
- Works for both booking bills and walk-in bills
- Guest bookings/walk-ins (no customerId) earn no points

### Ratings & Reviews
- One review per completed booking or walk-in (prevents fake reviews)
- Rating 1–5, optional comment
- Shop owner can reply publicly; reply can only be set once
- Aggregate average rating computed live (never stale)
- Email to shop owner when a new review is posted

### Dashboard
- Revenue summary with daily breakdown and top customers by spend
- Staff performance: completed bookings + revenue per staff member

### Customer Endpoints
- Profile view, update, soft-delete
- Own bookings list

### Email Notifications (Resend)
All emails are fire-and-forget — failures are logged but never propagate to the caller.

| Trigger | Customer | Owner |
|---|---|---|
| Customer creates booking | ✅ Booking received | ✅ New booking request |
| Staff accepts booking | ✅ Booking confirmed | — |
| Staff rejects booking | ✅ Booking declined | — |
| Customer cancels booking | ✅ Cancellation confirmed | ✅ Slot freed |
| Customer joins walk-in queue | ✅ Queue position + wait time | — |
| New review posted | — | ✅ New review with rating |

---

## Security Notes

- Passwords hashed with BCrypt
- JWTs stateless, signed with HMAC-SHA, secret via `JWT_SECRET` env var
- Short-lived access tokens (15 min default) + long-lived refresh tokens (30 days)
- Refresh token reuse detection — reuse invalidates the token family
- `shop_id` never trusted from client input for writes — always derived from `ShopStaff` membership
- All shop-scoped writes verify resource belongs to the correct shop (prevents cross-tenant ID guessing)
- Redis-backed rate limiting on auth endpoints
- `application.properties` excluded from git

---

## Known Tech Debt

1. **Booking slot-conflict race condition** (TOCTOU) — concurrent requests can both pass the conflict check before either saves. Needs a `UNIQUE(staff_id, booking_date, time_slot)` partial index excluding REJECTED/CANCELLED.
2. **Slot duration** — `Booking.timeSlot` is a point in time with no end time. Overlapping services for the same staff member aren't detected.
3. `ShopStaff.roleInShop` is a free-text string — owner check does exact match on `"Owner"`. Should be an enum.
4. **Dashboard top-customer aggregation** — in-memory grouping with per-booking `User` lookups. Fine at current scale, needs SQL aggregation at scale.
5. **Walk-in billing loyalty** — loyalty points on walk-in bills require a registered customer (`customerId`). Guests earn nothing (by design, but worth noting).

---

## Local Setup

Requires Java 21, PostgreSQL, and Redis.

```bash
createdb trimly
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Fill in your `application.properties`:

```properties
DB_URL=jdbc:postgresql://localhost:5432/trimly
DB_USERNAME=postgres
DB_PASSWORD=your_password
JWT_SECRET=a_long_random_secret_at_least_32_chars
RESEND_API_KEY=re_your_resend_key
FRONTEND_RESET_PASSWORD_URL=http://localhost:5173/reset-password
```

Redis must be running locally on the default port (6379) or configure via `REDIS_HOST` / `REDIS_PORT`.

Run:

```bash
mvn spring-boot:run
```

Flyway applies all 13 migrations automatically on startup.

### Quick smoke test

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Owner","email":"owner@example.com","phone":"9999999999","password":"password123","role":"OWNER"}'

# Create a shop (use token from above)
curl -X POST http://localhost:8080/api/v1/shops \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"My Shop","address":"123 Main St","locality":"Mumbai","timezone":"Asia/Kolkata"}'
```

---

## API Endpoints

All endpoints are prefixed with `/api/v1`.

### Auth
| Method | Path | Auth | Notes |
|---|---|---|---
| POST | `/auth/register` | Public | STAFF role blocked |
| POST | `/auth/login` | Public | Returns access + refresh token |
| POST | `/auth/refresh` | Public | Rotates refresh token |
| POST | `/auth/forgot-password` | Public | Sends reset email |
| POST | `/auth/reset-password` | Public | Validates token, sets new password |

### Shops
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/shops` | Authenticated | Re-issues JWT with new shopId |
| GET | `/shops/{shopId}` | Authenticated | |
| PUT | `/shops/{shopId}` | Owner | Update name/address/locality/timezone |
| DELETE | `/shops/{shopId}` | Owner | Soft delete |
| POST | `/shops/{shopId}/staff` | Owner | Add staff |
| GET | `/shops/{shopId}/staff` | Shop staff | |
| DELETE | `/shops/{shopId}/staff/{staffUserId}` | Owner | Remove staff |
| POST | `/shops/{shopId}/hours` | Owner | Set shop hours |
| GET | `/shops/{shopId}/hours` | Authenticated | |
| POST | `/shops/{shopId}/hours/closed-dates` | Owner | Add closed date |
| GET | `/shops/{shopId}/hours/closed-dates` | Authenticated | |

### Services
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/shops/{shopId}/services` | Shop staff | |
| GET | `/shops/{shopId}/services` | Authenticated | `?category=` optional |
| PUT | `/shops/{shopId}/services/{serviceId}` | Shop staff | |
| DELETE | `/shops/{shopId}/services/{serviceId}` | Shop staff | Soft delete |

### Bookings
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/shops/{shopId}/bookings` | Authenticated | Customer or staff (guest) booking |
| GET | `/shops/{shopId}/bookings` | Shop staff | `?date=`, `?status=` optional |
| PATCH | `/shops/{shopId}/bookings/{bookingId}/status` | Shop staff | Enforces state machine |
| PATCH | `/shops/{shopId}/bookings/{bookingId}/cancel` | Customer | Own bookings only |
| POST | `/shops/{shopId}/bookings/{bookingId}/bill` | Shop staff | Booking must be COMPLETED |

### Walk-in Queue
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/shops/{shopId}/walk-in-queue` | Authenticated | Customer self-join or staff adds guest |
| GET | `/shops/{shopId}/walk-in-queue` | Shop staff | Current queue |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/start` | Shop staff | Mark IN_SERVICE |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/complete` | Shop staff | Mark COMPLETED |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/cancel` | Shop staff | Mark CANCELLED |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/no-show` | Shop staff | Mark NO_SHOW |
| POST | `/shops/{shopId}/walk-in-queue/{entryId}/bill` | Shop staff | Entry must be COMPLETED |

### Reviews
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/shops/{shopId}/reviews` | Authenticated | One per completed booking or walk-in |
| GET | `/shops/{shopId}/reviews` | Public | `?page=&size=` |
| GET | `/shops/{shopId}/reviews/summary` | Public | Average rating + total count |
| POST | `/shops/{shopId}/reviews/{reviewId}/reply` | Owner | One reply per review |

### Loyalty
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/shops/{shopId}/loyalty/me` | Authenticated | Own balance at this shop |
| GET | `/shops/{shopId}/loyalty/me/transactions` | Authenticated | Own transaction history |
| GET | `/shops/{shopId}/loyalty/customer/{customerId}` | Shop staff | View customer balance |

### Dashboard
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/shops/{shopId}/dashboard/summary` | Shop staff | `?startDate=&endDate=` required |
| GET | `/shops/{shopId}/dashboard/staff-performance` | Shop staff | `?startDate=&endDate=` required |

### Customer
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/customers/me` | Authenticated | Own profile |
| PUT | `/customers/me` | Authenticated | Update name/phone |
| DELETE | `/customers/me` | Authenticated | Soft-delete account |
| GET | `/customers/me/bookings` | Authenticated | Own booking history |
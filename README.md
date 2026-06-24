# Trimly — Backend API

Production-ready REST API for a multi-shop barbershop SaaS. Manages bookings, walk-in queues, billing, loyalty points, staff, and real-time notifications across multiple shops.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language / Build | Java 21, Maven |
| Framework | Spring Boot 3.3.4 |
| Database | PostgreSQL |
| Cache / Rate Limiting | Redis |
| Migrations | Flyway (V1–V15) |
| ORM | Spring Data JPA (Hibernate) |
| Auth | Spring Security + JWT (JJWT 0.12.x) |
| Email | Resend (via REST) |
| Input Sanitization | Jsoup |
| Validation | Jakarta Bean Validation |
| Boilerplate | Lombok |
| Testing | JUnit 5, Mockito, AssertJ |

---

## Architecture

Single Spring Boot monolith. Every shop-scoped table carries a `shop_id` column — tenant isolation is enforced at the service layer via `ShopAccessService`, never trusted from client input.

```
Client (HTTP / REST + JWT)
   │
   ▼
Spring Security (JwtAuthFilter → SecurityContext)
   │
   ▼
Controllers (thin) → Services (business logic) → Spring Data JPA → PostgreSQL
                                                               ↘ Redis (rate limiting)
                                                               ↘ Resend (email)
```

### Package structure

```
com.trimly.backend
├── entity/        # JPA entities
├── enums/         # Role, StaffRole, ServiceCategory, BookingStatus,
│                  # WalkInStatus, PaymentMode, PaymentStatus
├── repository/    # Spring Data JPA repositories
├── service/       # Business logic, ShopAccessService, BookingMapper
├── controller/    # Thin REST controllers
├── security/      # JWT util, auth filter, UserDetails, SecurityConfig
├── config/        # WebConfig, GlobalExceptionHandler
├── interceptor/   # RateLimitInterceptor (Redis-backed)
├── dto/           # Request/response DTOs grouped by feature
├── util/          # Sanitizer (Jsoup-based HTML stripping)
└── exception/     # ResourceNotFoundException, ShopAccessDeniedException
```

---

## Data Model

| Entity | Key Fields | Notes |
|---|---|---|
| **User** | id, name, email, phone, passwordHash, role | role: OWNER / STAFF / CUSTOMER |
| **Shop** | id, name, address, locality, timezone, ownerId | Tenant root |
| **ShopStaff** | id, shopId, userId, roleInShop | roleInShop: OWNER / STAFF (enum) |
| **ShopHours** | id, shopId, dayOfWeek, openTime, closeTime, isOpen | Per-day operating hours |
| **ShopClosedDate** | id, shopId, date, reason | One-off closures |
| **ServiceItem** | id, shopId, category, name, price, estTimeMinutes | Soft-deletable |
| **Booking** | id, shopId, customerId (nullable), staffId, guestName, guestPhone, bookingDate, timeSlot, status | Guest fields for staff-added walk-ins |
| **BookingServiceItem** | id, bookingId, serviceId, priceAtBooking | Price snapshotted at booking time |
| **Bill** | id, shopId, bookingId (nullable), walkInQueueEntryId (nullable), totalAmount, loyaltyDiscount, finalAmount, paymentMode, paymentStatus | Covers both booking and walk-in billing |
| **WalkInQueueEntry** | id, shopId, customerId (nullable), guestName, guestPhone, preferredStaffId, status | WAITING / IN_SERVICE / COMPLETED / CANCELLED / NO_SHOW |
| **WalkInQueueServiceItem** | id, queueEntryId, serviceId, priceAtJoin | Price snapshotted at join time |
| **Review** | id, shopId, reviewerId, bookingId (nullable), rating, comment, ownerReply, ownerRepliedAt | One review per booking or walk-in |
| **LoyaltyAccount** | id, shopId, customerId, balance | Per-customer per-shop balance |
| **LoyaltyTransaction** | id, shopId, customerId, billId, type (EARN/REDEEM), points, balanceAfter | Full audit ledger |
| **RefreshToken** | id, userId, token, expiresAt | Persisted with reuse detection |
| **PasswordResetToken** | id, userId, token, expiresAt | Forgot-password flow |

---

## Features

### Auth
- Register / login with JWT access tokens (15 min) + refresh tokens (30 days)
- Refresh token rotation with reuse detection — reuse invalidates the token family
- Forgot password → reset password via email link (Resend)
- Redis-backed rate limiting on login, register, and forgot-password

### Shop Management
- Full shop CRUD with soft delete
- Staff add / remove (owner only); staff role stored as `OWNER` / `STAFF` enum
- Per-day operating hours + one-off closed dates
- Tenant isolation: `shop_id` never accepted from client on writes — always derived from authenticated staff membership

### Services
- Create / update / soft-delete service items
- Categories: MALE / FEMALE / CHILDREN
- Optional `?category=` filter on listing

### Bookings
- Customer self-booking or staff guest-booking (`guestName` / `guestPhone`)
- Slot-conflict detection — same staff + date + timeslot blocked (REJECTED/CANCELLED excluded)
- DB-level partial unique index prevents race-condition double-booking
- State machine: `PENDING → CONFIRMED / REJECTED → COMPLETED / CANCELLED`
- Paginated booking list with DB-level filtering by date and/or status — returns pagination metadata (`page`, `size`, `totalElements`, `totalPages`, `last`)
- Email notifications on every status transition

### Walk-in Queue
- Customer self-join or staff adds guest
- Predictive wait time based on queue position + service durations + shop hours
- Statuses: WAITING → IN_SERVICE → COMPLETED / CANCELLED / NO_SHOW
- Owner notified by email when any customer joins the queue
- Customer notified with position + estimated wait time

### Billing
- Generates bill for a completed booking or walk-in entry
- Double-bill prevented; total recalculated server-side (never trusted from client)
- Payment modes: CASH / UPI / ONLINE
- Loyalty points processed atomically at bill creation

### Loyalty Points
- **Earn**: 1 point per ₹10 spent on the final amount (after any discount)
- **Redeem**: pass `redeemPoints: true` — applies all available balance as discount, capped at 50% of bill value (1 point = ₹1 off)
- Full ledger in `loyalty_transactions`; per-shop balance in `loyalty_accounts`
- Guest bookings / walk-ins earn no points

### Reviews
- One review per completed booking or walk-in (prevents fake reviews)
- Rating 1–5 with optional comment
- Owner can post a public reply (once per review)
- Aggregate average rating computed live
- Owner notified by email on every new review

### Dashboard
- Daily revenue summary with date-range filtering
- Top customers by spend
- Staff performance: completed bookings + revenue per staff member

### Security
- Passwords hashed with BCrypt
- JWT signed with HMAC-SHA, secret via environment variable
- All free-text inputs (`guestName`, `guestPhone`, `comment`, `ownerReply`) sanitized with Jsoup before persistence — strips all HTML and script tags
- CORS configured for known frontend origins only

### Email Notifications

All emails are fire-and-forget — failures are logged but never propagate to the API response.

| Trigger | Customer | Owner |
|---|---|---|
| Booking created | ✅ Confirmation | ✅ New request |
| Booking confirmed | ✅ Confirmed | — |
| Booking rejected | ✅ Declined | — |
| Booking cancelled | ✅ Cancellation | ✅ Slot freed |
| Customer joins walk-in queue | ✅ Position + wait | ✅ New walk-in alert |
| New review posted | — | ✅ Review with rating |

---

## Local Setup

**Requirements:** Java 21, PostgreSQL, Redis

```bash
# 1. Create the database
createdb trimly

# 2. Configure environment
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Fill in `application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/trimly
spring.datasource.username=postgres
spring.datasource.password=your_password

trimly.jwt.secret=a_long_random_secret_at_least_32_chars
trimly.jwt.expiration-ms=900000
trimly.refresh-token.expiration-ms=2592000000

resend.api.key=re_your_resend_key
app.frontend.reset-password-url=http://localhost:5173/reset-password

spring.data.redis.host=localhost
spring.data.redis.port=6379
```

```bash
# 3. Run
mvn spring-boot:run
```

Flyway applies all 15 migrations automatically on startup.

### Quick smoke test

```bash
# Register as owner
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Ramesh Kumar","email":"owner@example.com","phone":"9999999999","password":"password123","role":"OWNER"}'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"owner@example.com","password":"password123"}'

# Create a shop (use token from login)
curl -X POST http://localhost:8080/api/v1/shops \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"Classic Cuts","address":"MG Road","locality":"Pune","timezone":"Asia/Kolkata"}'
```

---

## API Reference

All endpoints prefixed with `/api/v1`.

### Auth

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/auth/register` | Public | STAFF role blocked |
| POST | `/auth/login` | Public | Returns access + refresh token |
| POST | `/auth/refresh` | Public | Rotates refresh token |
| POST | `/auth/forgot-password` | Public | Sends reset email |
| POST | `/auth/reset-password` | Public | Sets new password |

### Shops

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/shops` | Authenticated | Creates shop |
| GET | `/shops/{shopId}` | Authenticated | |
| PUT | `/shops/{shopId}` | Owner | Update profile |
| DELETE | `/shops/{shopId}` | Owner | Soft delete |
| POST | `/shops/{shopId}/staff` | Owner | Add staff member |
| GET | `/shops/{shopId}/staff` | Shop staff | |
| DELETE | `/shops/{shopId}/staff/{staffUserId}` | Owner | Remove staff |
| POST | `/shops/{shopId}/hours` | Owner | Set operating hours |
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
| POST | `/shops/{shopId}/bookings` | Authenticated | Customer or staff (guest) |
| GET | `/shops/{shopId}/bookings` | Shop staff | `?date=` `?status=` `?page=` `?size=` |
| PATCH | `/shops/{shopId}/bookings/{bookingId}/status` | Shop staff | Enforces state machine |
| PATCH | `/shops/{shopId}/bookings/{bookingId}/cancel` | Customer | Own bookings only |
| POST | `/shops/{shopId}/bookings/{bookingId}/bill` | Shop staff | Booking must be COMPLETED |

### Walk-in Queue

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/shops/{shopId}/walk-in-queue` | Authenticated | Self-join or staff adds guest |
| GET | `/shops/{shopId}/walk-in-queue` | Shop staff | Current queue |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/start` | Shop staff | → IN_SERVICE |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/complete` | Shop staff | → COMPLETED |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/cancel` | Shop staff | → CANCELLED |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/no-show` | Shop staff | → NO_SHOW |
| POST | `/shops/{shopId}/walk-in-queue/{entryId}/bill` | Shop staff | Entry must be COMPLETED |

### Reviews

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/shops/{shopId}/reviews` | Authenticated | One per completed booking/walk-in |
| GET | `/shops/{shopId}/reviews` | Public | `?page=` `?size=` |
| GET | `/shops/{shopId}/reviews/summary` | Public | Average rating + count |
| POST | `/shops/{shopId}/reviews/{reviewId}/reply` | Owner | Once per review |

### Loyalty

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/shops/{shopId}/loyalty/me` | Authenticated | Own balance |
| GET | `/shops/{shopId}/loyalty/me/transactions` | Authenticated | Own transaction history |
| GET | `/shops/{shopId}/loyalty/customer/{customerId}` | Shop staff | Customer balance |

### Dashboard

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/shops/{shopId}/dashboard/summary` | Shop staff | `?startDate=` `?endDate=` |
| GET | `/shops/{shopId}/dashboard/staff-performance` | Shop staff | `?startDate=` `?endDate=` |

### Customer

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/customers/me` | Authenticated | Own profile |
| PUT | `/customers/me` | Authenticated | Update name/phone |
| DELETE | `/customers/me` | Authenticated | Soft-delete account |
| GET | `/customers/me/bookings` | Authenticated | Own booking history |
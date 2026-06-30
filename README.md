# Trimly — Backend API

Production-ready REST API for a multi-shop barbershop SaaS platform. Manages bookings (with reschedule), walk-in queues, real-time queue tracking, billing, payments, loyalty, referrals, inventory, staff scheduling and leave, cancellation policies, subscriptions, and push notifications across multiple shops.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language / Build | Java 21, Maven |
| Framework | Spring Boot 3.3.4 |
| Database | PostgreSQL |
| Migrations | Flyway (V1–V29) |
| ORM | Spring Data JPA (Hibernate) |
| Auth | Spring Security + JWT (JJWT 0.12.x) + Phone OTP (Twilio Verify) |
| API Docs | springdoc-openapi (Swagger UI) |
| Payments | Razorpay (order creation + signature-verified webhook) |
| Email | Resend (via REST) |
| Real-time | Server-Sent Events (SSE) |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| File Storage | AWS S3 (presigned URLs) |
| Rate Limiting | Redis |
| Input Sanitization | Jsoup |
| Validation | Jakarta Bean Validation |
| Boilerplate | Lombok |
| Testing | JUnit 5, Mockito, AssertJ |

---

## Architecture

Single Spring Boot monolith with tenant isolation enforced at the service layer. Every shop-scoped table carries a `shop_id` column — always derived from authenticated staff membership, never trusted from client input.

```
Client (HTTP / REST + JWT)
   │
   ▼
Spring Security (JwtAuthFilter → SecurityContext)
   │
   ▼
RateLimitInterceptor (Redis-backed, on auth/OTP/walk-in-queue routes)
   │
   ▼
Controllers (thin) → Services (business logic) → Spring Data JPA → PostgreSQL
                                                               ↘ Redis (rate limiting)
                                                               ↘ Resend (email)
                                                               ↘ Razorpay (payments)
                                                               ↘ FCM (push notifications)
                                                               ↘ AWS S3 (file storage)
                                                               ↘ Twilio Verify (phone OTP)
   │
   ▼
GlobalExceptionHandler — every exception path returns clean JSON
(validation errors, malformed JSON, missing params, bad UUIDs, wrong
HTTP verb, unknown routes, auth failures, payment gateway errors,
duplicate-slot conflicts) instead of leaking stack traces.
```

### Package structure

```
com.trimly.backend
├── entity/        # JPA entities
├── enums/         # Role, StaffRole, BookingStatus, WalkInStatus,
│                  # PaymentMode, PaymentStatus, LoyaltyTransactionType,
│                  # SubscriptionPlan
├── repository/    # Spring Data JPA repositories
├── service/       # Business logic
├── controller/    # Thin REST controllers
├── security/      # JWT util, auth filter, UserDetails, SecurityConfig
├── config/        # WebConfig, GlobalExceptionHandler, RedisConfig,
│                  # FirebaseConfig, S3Config, OpenApiConfig
├── interceptor/   # RateLimitInterceptor (Redis-backed)
├── scheduler/     # SubscriptionExpiryScheduler, ReminderScheduler
├── dto/           # Request/response DTOs grouped by feature
├── util/          # Sanitizer (Jsoup-based HTML stripping)
└── exception/     # ResourceNotFoundException, ShopAccessDeniedException
```

---

## Data Model

| Entity | Key Fields | Notes |
|---|---|---|
| **User** | id, name, email, phone, passwordHash, role, referralCode | role: OWNER / STAFF / CUSTOMER. Email + password nullable (phone-OTP-only accounts supported) |
| **Shop** | id, name, address, locality, timezone, ownerId | Tenant root |
| **ShopStaff** | id, shopId, userId, roleInShop | OWNER / STAFF |
| **ShopHours** | id, shopId, dayOfWeek, openTime, closeTime, isOpen | Per-day hours |
| **ShopClosedDate** | id, shopId, date, reason | One-off closures |
| **ShopSubscription** | id, shopId, plan, status, expiresAt | FREE / PRO / ENTERPRISE. Auto-downgraded to FREE at midnight when expired |
| **ShopCancellationPolicy** | id, shopId, minHoursBeforeCancel | One per shop. No policy = free cancellation anytime |
| **ServiceItem** | id, shopId, category, name, price, estTimeMinutes | Soft-deletable |
| **Booking** | id, shopId, customerId, staffId, guestName, bookingDate, timeSlot, status, rescheduledFromDate, rescheduledFromSlot | Guest support, reschedule audit trail |
| **BookingServiceItem** | id, bookingId, serviceId, priceAtBooking | Price snapshotted |
| **Bill** | id, shopId, bookingId, walkInQueueEntryId, totalAmount, paymentMode, paymentStatus | Covers both booking and walk-in |
| **WalkInQueueEntry** | id, shopId, customerId, guestName, preferredStaffId, assignedStaffId, status | WAITING → IN_PROGRESS → COMPLETED |
| **WalkInQueueServiceItem** | id, queueEntryId, serviceId, priceAtJoin | Price snapshotted |
| **ServiceRecord** | id, shopId, staffId, customerId, bookingId, walkInQueueEntryId, notes, productsUsed, photoUrls | Style history |
| **Review** | id, shopId, reviewerId, bookingId, rating, comment, ownerReply | One per booking/walk-in; only after a completed booking |
| **LoyaltyAccount** | id, shopId, customerId, balance | Per-shop balance |
| **LoyaltyTransaction** | id, shopId, customerId, billId, type, points, description | Full audit ledger |
| **Referral** | id, shopId, referrerId, referredId, referralCode, status, pointsAwarded | OPEN → PENDING → COMPLETED |
| **InventoryItem** | id, shopId, name, unit, quantityInStock, lowStockThreshold, costPerUnit | Stock tracking; fires low-stock FCM + email when threshold crossed |
| **InventoryUsage** | id, serviceRecordId, inventoryItemId, quantityUsed | Deducted on service record |
| **BarberProfile** | id, shopId, userId, bio, specialties, experienceYears, instagramHandle, photoUrl | Staff portfolio |
| **StaffShift** | id, shopId, staffUserId, dayOfWeek, startTime, endTime, isOff | Weekly recurring schedule |
| **StaffLeave** | id, shopId, staffUserId, leaveDate, reason | One-off date marked off, blocks slot availability |
| **FcmToken** | id, userId, token, deviceType | Per-device push token |
| **RefreshToken** | id, userId, token, expiresAt | Reuse detection |
| **PasswordResetToken** | id, userId, token, expiresAt | Forgot-password flow |

---

## Booking Lifecycle

```
PENDING ──accept──▶ ACCEPTED ──complete──▶ COMPLETED
   │                    │
   │                    └──cancel──▶ CANCELLED
   │
   └──reject──▶ REJECTED
   └──cancel──▶ CANCELLED
```

- **Reschedule** (`PENDING`/`ACCEPTED` only) moves the booking to a new date/slot, resets status to `PENDING`, frees the old slot, blocks the new slot if already taken, and respects the shop's cancellation policy window. The original slot is preserved in `rescheduledFromDate`/`rescheduledFromSlot` for audit.
- **Cancellation** respects the shop's `ShopCancellationPolicy` if one is set (e.g. "must cancel 2+ hours before"); owners/staff can override this when cancelling on a customer's behalf.
- Slot availability checks `ShopHours`, `ShopClosedDate`, `StaffShift.isOff` (recurring), and `StaffLeave` (one-off) — a barber's day off always blocks slots regardless of which mechanism marked it.
- Guest bookings (created by staff on a walk-in customer's behalf) have `customerId = null` and are identified by `guestName`/`guestPhone`; cancel/reschedule logic is null-safe for these.

---

## Authentication

Two login paths, both issuing the same JWT:

1. **Email + password** — `POST /auth/login`, `POST /auth/register`
2. **Phone OTP** — `POST /auth/send-otp?phone=`, `POST /auth/verify-otp` (Twilio Verify; auto-creates a user on first verify)

Rate-limited (Redis, per-IP): login (10/15min), register (5/hr), forgot-password (5/hr), send-otp (5/15min), walk-in-queue joins (20/hr).

---

## API Documentation

Interactive Swagger UI is available once the app is running:

```
http://localhost:8080/swagger-ui/index.html
```

Click **Authorize** and paste a Bearer JWT to test authenticated endpoints directly from the browser. Raw OpenAPI spec: `/v3/api-docs`.

---

## Error Handling

Every exception type returns a consistent JSON shape via `GlobalExceptionHandler`:

```json
{
  "timestamp": "2026-06-30T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Required parameter 'date' is missing.",
  "path": "/api/v1/shops/{shopId}/bookings/available-slots"
}
```

Covered: validation errors, malformed JSON bodies, missing query params, invalid path variable types (e.g. a non-UUID shop ID), unsupported HTTP methods, unknown routes, Spring Security access-denied, custom `ResourceNotFoundException` / `ShopAccessDeniedException`, duplicate-slot DB constraint violations, payment gateway/signature failures, and a final catch-all — nothing leaks a raw stack trace to the client.

---

## Endpoints

### Auth

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/auth/register` | Public | |
| POST | `/auth/login` | Public | |
| POST | `/auth/send-otp?phone=` | Public | Rate-limited |
| POST | `/auth/verify-otp` | Public | Auto-creates user on first verify |
| POST | `/auth/forgot-password` | Public | |
| POST | `/auth/reset-password` | Public | |
| POST | `/auth/refresh` | Public | |

### Shops

| Method | Path | Auth |
|---|---|---|
| POST | `/shops` | Authenticated |
| GET | `/shops/{shopId}` | Authenticated |
| PUT | `/shops/{shopId}` | Owner |
| DELETE | `/shops/{shopId}` | Owner |
| GET | `/shops/search?query=` | Public |
| GET | `/shops/localities` | Public |
| GET | `/shops/{shopId}/public` | Public |
| POST | `/shops/{shopId}/staff` | Owner |
| GET | `/shops/{shopId}/staff` | Shop staff |
| DELETE | `/shops/{shopId}/staff/{staffUserId}` | Owner |
| POST | `/shops/{shopId}/hours` | Owner |
| GET | `/shops/{shopId}/hours` | Public |
| POST | `/shops/{shopId}/hours/closed-dates` | Owner |

### Cancellation Policy

| Method | Path | Auth | Notes |
|---|---|---|---|
| PUT | `/shops/{shopId}/cancellation-policy` | Owner | Sets/updates min-hours window |
| GET | `/shops/{shopId}/cancellation-policy` | Public | Customer sees this before booking |
| DELETE | `/shops/{shopId}/cancellation-policy` | Owner | Removes policy → free cancellation |

### Staff Leave

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/shops/{shopId}/staff/{staffUserId}/leaves` | Owner | Marks a specific date off |
| GET | `/shops/{shopId}/staff/{staffUserId}/leaves` | Shop staff | |
| DELETE | `/shops/{shopId}/staff/{staffUserId}/leaves/{date}` | Owner | Cancels a marked leave |

### Subscription

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/shops/{shopId}/subscription` | Shop staff | Auto-creates FREE |
| PATCH | `/shops/{shopId}/subscription/upgrade?plan=PRO` | Owner | Creates Razorpay order |
| DELETE | `/shops/{shopId}/subscription` | Owner | Reverts to FREE |

Expired PRO/ENTERPRISE subscriptions are auto-downgraded to FREE by a daily scheduler at midnight.

### Services

| Method | Path | Auth |
|---|---|---|
| POST | `/shops/{shopId}/services` | Shop staff |
| GET | `/shops/{shopId}/services` | Public |
| PUT | `/shops/{shopId}/services/{serviceId}` | Shop staff |
| DELETE | `/shops/{shopId}/services/{serviceId}` | Shop staff |

### Bookings

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/shops/{shopId}/bookings` | Authenticated | Staff can book on behalf of a guest |
| GET | `/shops/{shopId}/bookings` | Shop staff | `?date=` `?status=` `?page=` `?size=` |
| GET | `/shops/{shopId}/bookings/available-slots` | Public | `?date=` `?staffId=` — respects hours, closed dates, shifts, leave |
| PATCH | `/shops/{shopId}/bookings/{bookingId}/status` | Shop staff | State machine enforced |
| PATCH | `/shops/{shopId}/bookings/{bookingId}/cancel` | Customer or shop staff | Respects cancellation policy (customer-side only) |
| PATCH | `/shops/{shopId}/bookings/{bookingId}/reschedule` | Customer | Moves to a new date/slot; resets to PENDING |
| POST | `/shops/{shopId}/bookings/{bookingId}/bill` | Shop staff | |
| POST | `/shops/{shopId}/bookings/{bookingId}/service-record` | Shop staff | |

### Walk-in Queue

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/shops/{shopId}/walk-in-queue` | Authenticated | Rate-limited |
| GET | `/shops/{shopId}/walk-in-queue` | Shop staff | |
| GET | `/shops/{shopId}/walk-in-queue/history` | Shop staff | `?page=` `?size=` |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/start` | Shop staff | |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/complete` | Shop staff | Notifies customer via FCM |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/cancel` | Shop staff | |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/no-show` | Shop staff | |
| POST | `/shops/{shopId}/walk-in-queue/{entryId}/bill` | Shop staff | |
| POST | `/shops/{shopId}/walk-in-queue/{entryId}/service-record` | Shop staff | |
| GET | `/shops/{shopId}/walk-in-queue/{entryId}/position` | Public | SSE stream |

### Payments

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/bills/{billId}/pay/online` | Authenticated | |
| POST | `/razorpay/webhook` | Public | HMAC signature verified |

### Reviews

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/shops/{shopId}/reviews` | Authenticated | Only after a completed booking |
| GET | `/shops/{shopId}/reviews` | Public | `?page=` `?size=` — paginated |
| GET | `/shops/{shopId}/reviews/summary` | Public | |
| POST | `/shops/{shopId}/reviews/{reviewId}/reply` | Owner | |

### Loyalty

| Method | Path | Auth |
|---|---|---|
| GET | `/shops/{shopId}/loyalty/me` | Authenticated |
| GET | `/shops/{shopId}/loyalty/me/transactions` | Authenticated |
| GET | `/shops/{shopId}/loyalty/customer/{customerId}` | Shop staff |
| POST | `/shops/{shopId}/loyalty/redeem` | Authenticated |

### Referrals

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/shops/{shopId}/referrals/my-code` | Authenticated | Generates code on first call |
| POST | `/shops/{shopId}/referrals/apply?code=` | Authenticated | |
| GET | `/shops/{shopId}/referrals/mine` | Authenticated | Own referral history |

### Inventory

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/shops/{shopId}/inventory` | Shop staff | |
| GET | `/shops/{shopId}/inventory` | Shop staff | |
| GET | `/shops/{shopId}/inventory/low-stock` | Shop staff | |
| PUT | `/shops/{shopId}/inventory/{itemId}` | Shop staff | |
| DELETE | `/shops/{shopId}/inventory/{itemId}` | Shop staff | |
| POST | `/shops/{shopId}/inventory/service-records/{serviceRecordId}/usage` | Shop staff | Fires low-stock alert if threshold crossed |

### Barber Profiles

| Method | Path | Auth |
|---|---|---|
| PUT | `/shops/{shopId}/staff/{staffUserId}/profile` | Shop staff |
| GET | `/shops/{shopId}/staff/{staffUserId}/profile` | Public |
| DELETE | `/shops/{shopId}/staff/{staffUserId}/profile` | Shop staff |
| GET | `/shops/{shopId}/profiles` | Public |

### Staff Shifts

| Method | Path | Auth |
|---|---|---|
| PUT | `/shops/{shopId}/staff/{staffUserId}/shifts` | Owner |
| GET | `/shops/{shopId}/staff/{staffUserId}/shifts` | Public |
| GET | `/shops/{shopId}/shifts` | Shop staff |
| DELETE | `/shops/{shopId}/staff/{staffUserId}/shifts/{dayOfWeek}` | Owner |

### Dashboard (PRO+ only)

| Method | Path | Auth |
|---|---|---|
| GET | `/shops/{shopId}/dashboard/summary` | Shop staff |
| GET | `/shops/{shopId}/dashboard/staff-performance` | Shop staff |
| GET | `/shops/{shopId}/dashboard/peak-hours` | Shop staff |
| GET | `/shops/{shopId}/dashboard/top-services` | Shop staff |
| GET | `/shops/{shopId}/dashboard/overview` | Shop staff |

### Customer

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/customers/me` | Authenticated | |
| PUT | `/customers/me` | Authenticated | |
| DELETE | `/customers/me` | Authenticated | Soft delete |
| GET | `/customers/me/bookings` | Authenticated | `?filter=upcoming\|past\|cancelled\|all` `?page=` `?size=` |
| GET | `/customers/me/walk-in-history` | Authenticated | `?page=` `?size=` |
| GET | `/customers/me/style-history` | Authenticated | |
| POST | `/customers/me/rebook-last?date=` | Authenticated | Book same as last time |

### File Upload

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/upload/presigned-url?folder=&filename=&contentType=` | Authenticated | Returns S3 presigned URL |

### Push Notifications

| Method | Path | Auth |
|---|---|---|
| POST | `/fcm/token` | Authenticated |
| DELETE | `/fcm/token?token=` | Authenticated |

---

## Background Jobs

| Scheduler | Frequency | Purpose |
|---|---|---|
| `SubscriptionExpiryScheduler` | Daily, midnight | Downgrades expired PRO/ENTERPRISE shops to FREE |
| `ReminderScheduler` | Hourly (configurable) | SMS + FCM reminder 1 hour before a booking |

---

## Running Locally

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
# fill in DB credentials, JWT secret, Resend/Twilio/Razorpay/Firebase/S3 keys

mvn clean install
mvn spring-boot:run
```

Flyway runs all migrations automatically on boot. Swagger UI: `http://localhost:8080/swagger-ui/index.html`.

### Required environment variables

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET`, `JWT_EXPIRATION_MS` | Auth token signing |
| `REFRESH_TOKEN_EXPIRATION_MS` | Refresh token TTL |
| `RESEND_API_KEY` | Transactional email |
| `CORS_ALLOWED_ORIGINS` | Comma-separated frontend origins (defaults to localhost in dev) |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | Payments |
| `TWILIO_*` | Phone OTP login |
| `FIREBASE_*` | Push notifications |
| `AWS_*` | S3 file uploads |
| `REDIS_HOST`, `REDIS_PORT` | Rate limiting |

---

## Migrations

29 Flyway migrations (`V1`–`V29`), covering: initial schema, soft deletes, walk-in queue, shop hours, reviews, loyalty, walk-in billing, staff roles, service records, Razorpay integration, barber profiles, staff shifts, inventory, referrals, FCM tokens, subscriptions, nullable email/password (phone-OTP support), booking reminders, subscription payments, staff leave, cancellation policy, and booking reschedule.
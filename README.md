# Trimly — Backend API

Production-ready REST API for a multi-shop barbershop SaaS platform. Manages bookings, walk-in queues, real-time queue tracking, billing, payments, loyalty, referrals, inventory, staff scheduling, subscriptions, and push notifications across multiple shops.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language / Build | Java 21, Maven |
| Framework | Spring Boot 3.3.4 |
| Database | PostgreSQL |
| Migrations | Flyway (V1–V23) |
| ORM | Spring Data JPA (Hibernate) |
| Auth | Spring Security + JWT (JJWT 0.12.x) |
| Payments | Razorpay (order creation + webhook) |
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
Controllers (thin) → Services (business logic) → Spring Data JPA → PostgreSQL
                                                               ↘ Redis (rate limiting)
                                                               ↘ Resend (email)
                                                               ↘ Razorpay (payments)
                                                               ↘ FCM (push notifications)
                                                               ↘ AWS S3 (file storage)
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
│                  # FirebaseConfig, S3Config
├── interceptor/   # RateLimitInterceptor (Redis-backed)
├── dto/           # Request/response DTOs grouped by feature
├── util/          # Sanitizer (Jsoup-based HTML stripping)
└── exception/     # ResourceNotFoundException, ShopAccessDeniedException
```

---

## Data Model

| Entity | Key Fields | Notes |
|---|---|---|
| **User** | id, name, email, phone, passwordHash, role, referralCode | role: OWNER / STAFF / CUSTOMER |
| **Shop** | id, name, address, locality, timezone, ownerId | Tenant root |
| **ShopStaff** | id, shopId, userId, roleInShop | OWNER / STAFF |
| **ShopHours** | id, shopId, dayOfWeek, openTime, closeTime, isOpen | Per-day hours |
| **ShopClosedDate** | id, shopId, date, reason | One-off closures |
| **ShopSubscription** | id, shopId, plan, status, expiresAt | FREE / PRO / ENTERPRISE |
| **ServiceItem** | id, shopId, category, name, price, estTimeMinutes | Soft-deletable |
| **Booking** | id, shopId, customerId, staffId, guestName, bookingDate, timeSlot, status | Guest support |
| **BookingServiceItem** | id, bookingId, serviceId, priceAtBooking | Price snapshotted |
| **Bill** | id, shopId, bookingId, walkInQueueEntryId, totalAmount, paymentMode, paymentStatus | Covers both booking and walk-in |
| **WalkInQueueEntry** | id, shopId, customerId, guestName, preferredStaffId, assignedStaffId, status | WAITING → IN_PROGRESS → COMPLETED |
| **WalkInQueueServiceItem** | id, queueEntryId, serviceId, priceAtJoin | Price snapshotted |
| **ServiceRecord** | id, shopId, staffId, customerId, bookingId, walkInQueueEntryId, notes, productsUsed, photoUrls | Style history |
| **Review** | id, shopId, reviewerId, bookingId, rating, comment, ownerReply | One per booking/walk-in |
| **LoyaltyAccount** | id, shopId, customerId, balance | Per-shop balance |
| **LoyaltyTransaction** | id, shopId, customerId, billId, type, points, description | Full audit ledger |
| **Referral** | id, shopId, referrerId, referredId, referralCode, status, pointsAwarded | OPEN → PENDING → COMPLETED |
| **InventoryItem** | id, shopId, name, unit, quantityInStock, lowStockThreshold, costPerUnit | Stock tracking |
| **InventoryUsage** | id, serviceRecordId, inventoryItemId, quantityUsed | Deducted on service record |
| **BarberProfile** | id, shopId, userId, bio, specialties, experienceYears, instagramHandle, photoUrl | Staff portfolio |
| **StaffShift** | id, shopId, staffUserId, dayOfWeek, startTime, endTime, isOff | Weekly schedule |
| **FcmToken** | id, userId, token, deviceType | Per-device push token |
| **RefreshToken** | id, userId, token, expiresAt | Reuse detection |
| **PasswordResetToken** | id, userId, token, expiresAt | Forgot-password flow |

---

## Features

### Auth
- Register / login with JWT access tokens (15 min) + refresh tokens (30 days)
- Refresh token rotation with reuse detection
- Forgot password → reset via email link
- Redis-backed rate limiting on auth endpoints

### Subscription Plans

| Feature | FREE | PRO | ENTERPRISE |
|---|---|---|---|
| Max staff | 2 | 10 | Unlimited |
| Max bookings/month | 100 | 1,000 | Unlimited |
| Dashboard & analytics | ❌ | ✅ | ✅ |
| Multi-branch | ❌ | ❌ | ✅ |

Limits enforced server-side on every relevant operation. Dashboard endpoints return `403` on FREE plan.

### Bookings
- Customer self-booking or staff guest-booking
- Slot-conflict detection with DB-level partial unique index
- State machine: `PENDING → ACCEPTED / REJECTED → COMPLETED / CANCELLED`
- Email + FCM push on every status transition
- **"Book same as last time"** — single endpoint re-books last service on a new date

### Walk-in Queue
- Self-join or staff adds guest
- Predictive wait time: accounts for in-progress services, scheduled bookings, and shop hours
- **Live queue position via SSE** — streams position + wait time every 10 seconds; auto-closes on completion
- Email notifications to customer and owner on join

### Payments (Razorpay)
- Create Razorpay order from bill → `payment.captured` webhook → mark PAID
- HMAC-SHA256 webhook signature verification
- Loyalty points awarded automatically on capture
- Idempotent webhook handler

### Loyalty Points
- Earn: 1 point per ₹10 spent
- Redeem: capped at 50% of bill (1 point = ₹1)
- Full ledger in `loyalty_transactions`

### Referral Program
- Each customer gets a unique shareable code per shop
- Referred customer applies code before their first booking
- On booking acceptance → referral auto-completes → referrer earns 100 loyalty points
- Full referral history available

### Inventory Management
- Track products (shampoo, hair color, etc.) with stock levels
- Low-stock alerts via `GET /inventory/low-stock`
- Stock automatically deducted when staff records inventory usage against a service record

### Barber Portfolio
- Staff can maintain bio, specialties, years of experience, Instagram handle, photo URL
- Publicly accessible per shop

### Staff Shift Scheduling
- Define weekly shifts per staff member (day, start time, end time, day-off flag)
- Owner views full shop schedule in one call

### Style History (Service Records)
- Staff creates a record after each service: notes, products used, photo URLs
- Powers "book same as last time"
- Customer views full history across all shops

### Reviews
- One review per completed booking or walk-in (no fake reviews)
- Rating 1–5 + optional comment
- Owner can reply once per review
- Aggregate rating computed live

### Dashboard (PRO+)
- Revenue summary with date-range + daily breakdown
- Staff performance: bookings + revenue per staff member
- Peak hours analysis
- Top services by booking count
- Shop overview stats

### Push Notifications (FCM)
- Customers register device tokens via `POST /fcm/token`
- Push sent alongside email on booking confirmed, accepted, rejected, cancelled
- Firebase disabled gracefully if service account not configured

### File Storage (S3)
- Backend generates presigned upload URLs (10 min expiry)
- Frontend uploads directly to S3 — no file bytes pass through the backend
- Used for service record photos and barber profile photos

### Security
- BCrypt password hashing
- JWT signed with HMAC-SHA, secret via env var
- All free-text sanitized with Jsoup before persistence
- CORS locked to known frontend origins

---

## Local Setup

**Requirements:** Java 21, PostgreSQL, Redis

```bash
# 1. Clone and create DB
git clone https://github.com/ShubhamPDev7/trimly-backend.git
cd trimly-backend
createdb trimly

# 2. Configure
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

razorpay.key.id=rzp_test_your_key
razorpay.key.secret=your_secret
razorpay.webhook.secret=your_webhook_secret

aws.access-key-id=your_key
aws.secret-access-key=your_secret
aws.region=ap-south-1
aws.s3.bucket=your_bucket

firebase.service-account-path=
```

> S3 and FCM are optional — the app boots and runs fully without them.

```bash
# 3. Run
mvn spring-boot:run
```

Flyway applies all 23 migrations automatically on startup.

---

## API Reference

All endpoints prefixed with `/api/v1`.

### Auth

| Method | Path | Auth |
|---|---|---|
| POST | `/auth/register` | Public |
| POST | `/auth/login` | Public |
| POST | `/auth/refresh` | Public |
| POST | `/auth/logout` | Public |
| POST | `/auth/forgot-password` | Public |
| POST | `/auth/reset-password` | Public |

### Shops

| Method | Path | Auth |
|---|---|---|
| POST | `/shops` | Authenticated |
| GET | `/shops/{shopId}` | Authenticated |
| PUT | `/shops/{shopId}` | Owner |
| DELETE | `/shops/{shopId}` | Owner |
| POST | `/shops/{shopId}/staff` | Owner |
| GET | `/shops/{shopId}/staff` | Shop staff |
| DELETE | `/shops/{shopId}/staff/{staffUserId}` | Owner |
| POST | `/shops/{shopId}/hours` | Owner |
| GET | `/shops/{shopId}/hours` | Public |
| POST | `/shops/{shopId}/hours/closed-dates` | Owner |

### Subscription

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/shops/{shopId}/subscription` | Shop staff | Auto-creates FREE |
| PATCH | `/shops/{shopId}/subscription/upgrade?plan=PRO` | Owner | |
| DELETE | `/shops/{shopId}/subscription` | Owner | Reverts to FREE |

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
| POST | `/shops/{shopId}/bookings` | Authenticated | |
| GET | `/shops/{shopId}/bookings` | Shop staff | `?date=` `?status=` `?page=` `?size=` |
| GET | `/shops/{shopId}/bookings/available-slots` | Public | `?date=` `?staffId=` |
| PATCH | `/shops/{shopId}/bookings/{bookingId}/status` | Shop staff | State machine enforced |
| PATCH | `/shops/{shopId}/bookings/{bookingId}/cancel` | Customer | Own bookings only |
| POST | `/shops/{shopId}/bookings/{bookingId}/bill` | Shop staff | |
| POST | `/shops/{shopId}/bookings/{bookingId}/service-record` | Shop staff | |

### Walk-in Queue

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/shops/{shopId}/walk-in-queue` | Authenticated | |
| GET | `/shops/{shopId}/walk-in-queue` | Shop staff | |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/start` | Shop staff | |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/complete` | Shop staff | |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/cancel` | Shop staff | |
| PATCH | `/shops/{shopId}/walk-in-queue/{entryId}/no-show` | Shop staff | |
| POST | `/shops/{shopId}/walk-in-queue/{entryId}/bill` | Shop staff | |
| POST | `/shops/{shopId}/walk-in-queue/{entryId}/service-record` | Shop staff | |
| GET | `/shops/{shopId}/walk-in-queue/{entryId}/position` | Public | SSE stream |

### Payments

| Method | Path | Auth |
|---|---|---|
| POST | `/bills/{billId}/pay/online` | Authenticated |
| POST | `/razorpay/webhook` | Public |

### Reviews

| Method | Path | Auth |
|---|---|---|
| POST | `/shops/{shopId}/reviews` | Authenticated |
| GET | `/shops/{shopId}/reviews` | Public |
| GET | `/shops/{shopId}/reviews/summary` | Public |
| POST | `/shops/{shopId}/reviews/{reviewId}/reply` | Owner |

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

| Method | Path | Auth |
|---|---|---|
| POST | `/shops/{shopId}/inventory` | Shop staff |
| GET | `/shops/{shopId}/inventory` | Shop staff |
| GET | `/shops/{shopId}/inventory/low-stock` | Shop staff |
| PUT | `/shops/{shopId}/inventory/{itemId}` | Shop staff |
| DELETE | `/shops/{shopId}/inventory/{itemId}` | Shop staff |
| POST | `/shops/{shopId}/inventory/service-records/{serviceRecordId}/usage` | Shop staff |

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
| GET | `/customers/me/bookings` | Authenticated | |
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
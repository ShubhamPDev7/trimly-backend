
CREATE TABLE users (
                       id            UUID PRIMARY KEY,
                       name          VARCHAR(255) NOT NULL,
                       email         VARCHAR(255) NOT NULL,
                       phone         VARCHAR(255),
                       password_hash VARCHAR(255) NOT NULL,
                       role          VARCHAR(20)  NOT NULL,
                       created_at    TIMESTAMPTZ  NOT NULL,
                       CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE shops (
                       id         UUID PRIMARY KEY,
                       name       VARCHAR(255) NOT NULL,
                       address    VARCHAR(255),
                       locality   VARCHAR(255),
                       owner_id   UUID         NOT NULL,
                       created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE shop_staff (
                            id           UUID PRIMARY KEY,
                            shop_id      UUID         NOT NULL,
                            user_id      UUID         NOT NULL,
                            role_in_shop VARCHAR(255) NOT NULL,
                            CONSTRAINT uk_shop_staff_shop_user UNIQUE (shop_id, user_id)
);

CREATE TABLE services (
                          id               UUID PRIMARY KEY,
                          shop_id          UUID         NOT NULL,
                          category         VARCHAR(20)  NOT NULL,
                          name             VARCHAR(255) NOT NULL,
                          price            NUMERIC(10, 2) NOT NULL,
                          est_time_minutes INTEGER,
                          image_url        VARCHAR(255)
);

CREATE TABLE bookings (
                          id            UUID PRIMARY KEY,
                          shop_id       UUID         NOT NULL,
                          customer_id   UUID,
                          staff_id      UUID,
                          guest_name    VARCHAR(255),
                          guest_phone   VARCHAR(255),
                          booking_date  DATE         NOT NULL,
                          time_slot     TIME         NOT NULL,
                          status        VARCHAR(20)  NOT NULL,
                          created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE booking_services (
                                  id               UUID PRIMARY KEY,
                                  booking_id       UUID           NOT NULL,
                                  service_id       UUID           NOT NULL,
                                  price_at_booking NUMERIC(10, 2) NOT NULL
);

CREATE TABLE bills (
                       id             UUID PRIMARY KEY,
                       shop_id        UUID           NOT NULL,
                       booking_id     UUID           NOT NULL,
                       total_amount   NUMERIC(10, 2) NOT NULL,
                       payment_mode   VARCHAR(20)    NOT NULL,
                       payment_status VARCHAR(20)    NOT NULL,
                       created_at     TIMESTAMPTZ    NOT NULL,
                       CONSTRAINT uk_bills_booking_id UNIQUE (booking_id)
);
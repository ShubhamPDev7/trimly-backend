CREATE TABLE service_records (
                                 id                     UUID PRIMARY KEY,
                                 shop_id                UUID        NOT NULL,
                                 staff_id               UUID        NOT NULL,
                                 customer_id            UUID,
                                 booking_id             UUID,
                                 walk_in_queue_entry_id UUID,
                                 notes                  TEXT,
                                 products_used          TEXT,
                                 photo_urls             TEXT,
                                 created_at             TIMESTAMPTZ NOT NULL,
                                 CONSTRAINT uk_service_records_booking_id
                                     UNIQUE (booking_id),
                                 CONSTRAINT uk_service_records_walk_in_entry_id
                                     UNIQUE (walk_in_queue_entry_id),
                                 CONSTRAINT chk_service_records_source
                                     CHECK (
                                         (booking_id IS NOT NULL AND walk_in_queue_entry_id IS NULL)
                                             OR
                                         (booking_id IS NULL AND walk_in_queue_entry_id IS NOT NULL)
                                         )
);

CREATE INDEX idx_service_records_shop_id    ON service_records (shop_id);
CREATE INDEX idx_service_records_customer_id ON service_records (customer_id);
CREATE INDEX idx_service_records_staff_id   ON service_records (staff_id);
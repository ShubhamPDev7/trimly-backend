CREATE TABLE walk_in_queue_entries (
                                       id                 UUID PRIMARY KEY,
                                       shop_id            UUID         NOT NULL,
                                       customer_id        UUID,
                                       guest_name         VARCHAR(255),
                                       guest_phone        VARCHAR(50),
                                       preferred_staff_id UUID,
                                       assigned_staff_id  UUID,
                                       status             VARCHAR(20)  NOT NULL,
                                       joined_at          TIMESTAMPTZ  NOT NULL,
                                       started_at         TIMESTAMPTZ,
                                       completed_at       TIMESTAMPTZ
);

CREATE TABLE walk_in_queue_service_items (
                                             id             UUID PRIMARY KEY,
                                             queue_entry_id UUID           NOT NULL,
                                             service_id     UUID           NOT NULL,
                                             price_at_join  NUMERIC(10, 2) NOT NULL
);

CREATE INDEX idx_walk_in_queue_entries_shop_id_status ON walk_in_queue_entries (shop_id, status);
CREATE INDEX idx_walk_in_queue_service_items_queue_entry_id ON walk_in_queue_service_items (queue_entry_id);
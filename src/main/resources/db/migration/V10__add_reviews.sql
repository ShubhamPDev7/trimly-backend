CREATE TABLE reviews (
                         id                      UUID PRIMARY KEY,
                         shop_id                 UUID        NOT NULL,
                         reviewer_id             UUID        NOT NULL,
                         booking_id              UUID,
                         walk_in_queue_entry_id  UUID,
                         rating                  INTEGER     NOT NULL CHECK (rating >= 1 AND rating <= 5),
                         comment                 TEXT,
                         owner_reply             TEXT,
                         owner_replied_at        TIMESTAMPTZ,
                         created_at              TIMESTAMPTZ NOT NULL,
                         UNIQUE (booking_id),
                         UNIQUE (walk_in_queue_entry_id)
);

CREATE INDEX idx_reviews_shop_id ON reviews (shop_id);
CREATE INDEX idx_reviews_reviewer_id ON reviews (reviewer_id);
CREATE TABLE staff_leaves (
                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              shop_id UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
                              staff_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                              leave_date DATE NOT NULL,
                              reason VARCHAR(255),
                              created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                              CONSTRAINT uq_staff_leave UNIQUE (shop_id, staff_user_id, leave_date)
);

CREATE INDEX idx_staff_leaves_shop_staff ON staff_leaves(shop_id, staff_user_id);
CREATE INDEX idx_staff_leaves_date ON staff_leaves(leave_date);
CREATE TABLE staff_shifts (
                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              shop_id UUID NOT NULL REFERENCES shops(id),
                              staff_user_id UUID NOT NULL REFERENCES users(id),
                              day_of_week INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
                              start_time TIME NOT NULL,
                              end_time TIME NOT NULL,
                              is_off BOOLEAN NOT NULL DEFAULT FALSE,
                              created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                              updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                              CONSTRAINT uk_staff_shift_day UNIQUE (shop_id, staff_user_id, day_of_week)
);

CREATE INDEX idx_staff_shifts_shop_id ON staff_shifts(shop_id);
CREATE INDEX idx_staff_shifts_staff_user_id ON staff_shifts(staff_user_id);
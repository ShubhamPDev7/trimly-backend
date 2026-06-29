CREATE TABLE shop_cancellation_policies (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                            shop_id UUID NOT NULL UNIQUE REFERENCES shops(id) ON DELETE CASCADE,
                                            min_hours_before_cancel INT NOT NULL,
                                            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
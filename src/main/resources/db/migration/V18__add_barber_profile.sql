CREATE TABLE barber_profiles (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 shop_id UUID NOT NULL REFERENCES shops(id),
                                 user_id UUID NOT NULL REFERENCES users(id),
                                 bio TEXT,
                                 specialties TEXT,
                                 experience_years INTEGER,
                                 instagram_handle VARCHAR(100),
                                 photo_url VARCHAR(500),
                                 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                 CONSTRAINT uk_barber_profile_shop_user UNIQUE (shop_id, user_id)
);

CREATE INDEX idx_barber_profiles_shop_id ON barber_profiles(shop_id);
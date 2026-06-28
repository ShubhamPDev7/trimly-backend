CREATE TABLE referrals (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           shop_id UUID NOT NULL REFERENCES shops(id),
                           referrer_id UUID NOT NULL REFERENCES users(id),
                           referred_id UUID NOT NULL REFERENCES users(id),
                           referral_code VARCHAR(20) NOT NULL,
                           status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                           points_awarded INTEGER NOT NULL DEFAULT 0,
                           created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                           completed_at TIMESTAMP WITH TIME ZONE,
                           CONSTRAINT uk_referral_shop_referred UNIQUE (shop_id, referred_id)
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS referral_code VARCHAR(20) UNIQUE;

CREATE INDEX idx_referrals_shop_id ON referrals(shop_id);
CREATE INDEX idx_referrals_referrer_id ON referrals(referrer_id);
CREATE INDEX idx_referrals_referral_code ON referrals(referral_code);
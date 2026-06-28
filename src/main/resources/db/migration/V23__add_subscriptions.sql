CREATE TABLE shop_subscriptions (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    shop_id UUID NOT NULL REFERENCES shops(id),
                                    plan VARCHAR(20) NOT NULL DEFAULT 'FREE',
                                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                    expires_at TIMESTAMP WITH TIME ZONE,
                                    razorpay_subscription_id VARCHAR(100),
                                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                    CONSTRAINT uk_shop_subscription UNIQUE (shop_id)
);

CREATE INDEX idx_shop_subscriptions_shop_id ON shop_subscriptions(shop_id);
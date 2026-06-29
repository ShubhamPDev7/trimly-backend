ALTER TABLE shop_subscriptions ADD COLUMN IF NOT EXISTS razorpay_order_id VARCHAR(100);
ALTER TABLE shop_subscriptions ADD COLUMN IF NOT EXISTS pending_plan VARCHAR(20);
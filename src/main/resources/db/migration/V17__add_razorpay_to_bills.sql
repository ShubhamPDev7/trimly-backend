-- Razorpay order and payment tracking
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS razorpay_order_id   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS razorpay_payment_id  VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bills_razorpay_order_id
    ON bills (razorpay_order_id)
    WHERE razorpay_order_id IS NOT NULL;
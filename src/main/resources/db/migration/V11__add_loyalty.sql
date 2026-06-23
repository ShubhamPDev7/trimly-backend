CREATE TABLE loyalty_accounts (
                                  id          UUID PRIMARY KEY,
                                  shop_id     UUID        NOT NULL,
                                  customer_id UUID        NOT NULL,
                                  balance     INTEGER     NOT NULL DEFAULT 0,
                                  created_at  TIMESTAMPTZ NOT NULL,
                                  updated_at  TIMESTAMPTZ NOT NULL,
                                  UNIQUE (shop_id, customer_id)
);

CREATE TABLE loyalty_transactions (
                                      id                  UUID PRIMARY KEY,
                                      loyalty_account_id  UUID        NOT NULL,
                                      shop_id             UUID        NOT NULL,
                                      customer_id         UUID        NOT NULL,
                                      type                VARCHAR(20) NOT NULL,
                                      points              INTEGER     NOT NULL,
                                      bill_id             UUID,
                                      description         TEXT,
                                      created_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_loyalty_accounts_shop_customer ON loyalty_accounts (shop_id, customer_id);
CREATE INDEX idx_loyalty_transactions_account_id ON loyalty_transactions (loyalty_account_id);
CREATE INDEX idx_loyalty_transactions_customer_id ON loyalty_transactions (customer_id);
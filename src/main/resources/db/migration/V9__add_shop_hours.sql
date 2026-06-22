ALTER TABLE shops ADD COLUMN timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata';

CREATE TABLE shop_hours (
                            id           UUID PRIMARY KEY,
                            shop_id      UUID        NOT NULL,
                            day_of_week  INTEGER     NOT NULL,
                            open_time    TIME,
                            close_time   TIME,
                            is_closed    BOOLEAN     NOT NULL DEFAULT FALSE,
                            UNIQUE (shop_id, day_of_week)
);

CREATE TABLE shop_closed_dates (
                                   id          UUID PRIMARY KEY,
                                   shop_id     UUID        NOT NULL,
                                   closed_date DATE        NOT NULL,
                                   reason      VARCHAR(255),
                                   UNIQUE (shop_id, closed_date)
);

CREATE INDEX idx_shop_hours_shop_id ON shop_hours (shop_id);
CREATE INDEX idx_shop_closed_dates_shop_id_date ON shop_closed_dates (shop_id, closed_date);
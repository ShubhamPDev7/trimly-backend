CREATE TABLE inventory_items (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 shop_id UUID NOT NULL REFERENCES shops(id),
                                 name VARCHAR(255) NOT NULL,
                                 description TEXT,
                                 unit VARCHAR(50),
                                 quantity_in_stock NUMERIC(10, 2) NOT NULL DEFAULT 0,
                                 low_stock_threshold NUMERIC(10, 2),
                                 cost_per_unit NUMERIC(10, 2),
                                 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE service_record_inventory_usage (
                                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                service_record_id UUID NOT NULL REFERENCES service_records(id),
                                                inventory_item_id UUID NOT NULL REFERENCES inventory_items(id),
                                                quantity_used NUMERIC(10, 2) NOT NULL,
                                                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_inventory_items_shop_id ON inventory_items(shop_id);
CREATE INDEX idx_inventory_usage_record_id ON service_record_inventory_usage(service_record_id);
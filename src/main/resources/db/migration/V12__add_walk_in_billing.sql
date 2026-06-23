ALTER TABLE bills ADD COLUMN walk_in_queue_entry_id UUID;

CREATE UNIQUE INDEX idx_bills_walk_in_queue_entry_id
    ON bills (walk_in_queue_entry_id)
    WHERE walk_in_queue_entry_id IS NOT NULL;
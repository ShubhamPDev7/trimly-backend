ALTER TABLE bookings
    ADD COLUMN rescheduled_from_date DATE,
    ADD COLUMN rescheduled_from_slot TIME;
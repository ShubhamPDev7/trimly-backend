CREATE UNIQUE INDEX idx_bookings_staff_slot_unique
    ON bookings (staff_id, booking_date, time_slot)
    WHERE status NOT IN ('CANCELLED', 'REJECTED');
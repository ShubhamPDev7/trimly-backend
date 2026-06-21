CREATE UNIQUE INDEX uq_bookings_active_slot
    ON bookings (shop_id, staff_id, booking_date, time_slot)
    WHERE status IN ('PENDING', 'ACCEPTED', 'COMPLETED');
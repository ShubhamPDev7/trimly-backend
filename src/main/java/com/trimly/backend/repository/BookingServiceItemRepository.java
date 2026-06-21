package com.trimly.backend.repository;

import com.trimly.backend.entity.BookingServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingServiceItemRepository extends JpaRepository<BookingServiceItem, UUID> {

    List<BookingServiceItem> findByBookingId(UUID bookingId);

    List<BookingServiceItem> findByBookingIdIn(List<UUID> bookingIds);


}
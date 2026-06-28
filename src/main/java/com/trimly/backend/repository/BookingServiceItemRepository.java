package com.trimly.backend.repository;

import com.trimly.backend.entity.BookingServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BookingServiceItemRepository extends JpaRepository<BookingServiceItem, UUID> {

    List<BookingServiceItem> findByBookingId(UUID bookingId);

    List<BookingServiceItem> findByBookingIdIn(List<UUID> bookingIds);

    /**
     * Returns [serviceId, bookingCount, totalRevenue] for all services in the given booking IDs.
     * Used for top services analytics.
     */
    @Query("""
            SELECT bsi.serviceId, COUNT(bsi.id), SUM(bsi.priceAtBooking)
            FROM BookingServiceItem bsi
            WHERE bsi.bookingId IN :bookingIds
            GROUP BY bsi.serviceId
            ORDER BY SUM(bsi.priceAtBooking) DESC
            """)
    List<Object[]> findServiceStatsByBookingIds(List<UUID> bookingIds);

}
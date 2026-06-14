package com.trimly.backend.repository;

import com.trimly.backend.entity.Booking;
import com.trimly.backend.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByShopId(UUID shopId);
    List<Booking> findByShopIdAndBookingDate(UUID shopId, LocalDate bookingDate);
    List<Booking> findByCustomerId(UUID customerId);
    List<Booking> findByStaffIdAndBookingDate(UUID staffId, LocalDate bookingDate);
    List<Booking> findByShopIdAndStatus(UUID shopId, BookingStatus status);

}

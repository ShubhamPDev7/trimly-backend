package com.trimly.backend.repository;

import com.trimly.backend.entity.Booking;
import com.trimly.backend.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Page<Booking> findByShopId(UUID shopId, Pageable pageable);
    Page<Booking> findByShopIdAndBookingDate(UUID shopId, LocalDate bookingDate, Pageable pageable);
    Page<Booking> findByShopIdAndStatus(UUID shopId, BookingStatus status, Pageable pageable);
    Page<Booking> findByShopIdAndBookingDateAndStatus(UUID shopId, LocalDate bookingDate, BookingStatus status, Pageable pageable);

    List<Booking> findByShopIdAndBookingDate(UUID shopId, LocalDate bookingDate);
    List<Booking> findByCustomerId(UUID customerId);
    List<Booking> findByStaffIdAndBookingDate(UUID staffId, LocalDate bookingDate);
    List<Booking> findByShopIdAndStatus(UUID shopId, BookingStatus status);
    List<Booking> findByShopIdAndBookingDateBetween(UUID shopId, LocalDate startDate, LocalDate endDate);

}
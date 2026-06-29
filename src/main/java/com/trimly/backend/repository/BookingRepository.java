package com.trimly.backend.repository;

import com.trimly.backend.entity.Booking;
import com.trimly.backend.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    int countByShopIdAndBookingDateBetween(UUID shopId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT b FROM Booking b WHERE b.bookingDate = :date AND b.timeSlot BETWEEN :from AND :to AND b.status = 'ACCEPTED' AND b.reminderSent = false")
    List<Booking> findUpcomingBookingsForReminder(@Param("date") java.time.LocalDate date,
                                                  @Param("from") java.time.LocalTime from,
                                                  @Param("to") java.time.LocalTime to);


    Page<Booking> findByCustomerId(UUID customerId, Pageable pageable);
    Page<Booking> findByCustomerIdAndStatus(UUID customerId, BookingStatus status, Pageable pageable);
    Page<Booking> findByCustomerIdAndBookingDateLessThan(UUID customerId, LocalDate date, Pageable pageable);

    @Query("SELECT b FROM Booking b WHERE b.customerId = :customerId AND b.bookingDate >= :date AND b.status IN :statuses")
    Page<Booking> findByCustomerIdAndBookingDateGreaterThanEqualAndStatusIn(
            @Param("customerId") UUID customerId,
            @Param("date") LocalDate date,
            @Param("statuses") List<BookingStatus> statuses,
            Pageable pageable);

}
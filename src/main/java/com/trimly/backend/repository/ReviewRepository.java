package com.trimly.backend.repository;

import com.trimly.backend.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByShopId(UUID shopId, Pageable pageable);

    Optional<Review> findByBookingId(UUID bookingId);

    Optional<Review> findByWalkInQueueEntryId(UUID walkInQueueEntryId);

    boolean existsByBookingId(UUID bookingId);

    boolean existsByWalkInQueueEntryId(UUID walkInQueueEntryId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.shopId = :shopId")
    Double findAverageRatingByShopId(UUID shopId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.shopId = :shopId")
    long countByShopId(UUID shopId);

    @Query("SELECT AVG(r.rating) FROM Review r JOIN Booking b ON r.bookingId = b.id WHERE b.staffId = :staffId AND r.shopId = :shopId")
    Double findAverageRatingByStaffId(UUID staffId, UUID shopId);

    @Query("SELECT COUNT(r) FROM Review r JOIN Booking b ON r.bookingId = b.id WHERE b.staffId = :staffId AND r.shopId = :shopId")
    long countByStaffId(UUID staffId, UUID shopId);

}
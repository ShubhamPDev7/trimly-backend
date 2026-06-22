package com.trimly.backend.service;

import com.trimly.backend.dto.review.OwnerReplyRequest;
import com.trimly.backend.dto.review.ReviewRequest;
import com.trimly.backend.dto.review.ReviewResponse;
import com.trimly.backend.dto.review.ShopRatingSummaryResponse;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.Review;
import com.trimly.backend.entity.WalkInQueueEntry;
import com.trimly.backend.enums.BookingStatus;
import com.trimly.backend.enums.WalkInStatus;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.ReviewRepository;
import com.trimly.backend.repository.WalkInQueueEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final WalkInQueueEntryRepository walkInQueueEntryRepository;
    private final ShopAccessService shopAccessService;

    @Transactional
    public ReviewResponse createReview(UUID shopId, ReviewRequest request, UUID currentUserId) {
        if (request.bookingId() == null && request.walkInQueueEntryId() == null) {
            throw new IllegalArgumentException("Either bookingId or walkInQueueEntryId must be provided.");
        }

        if (request.bookingId() != null && request.walkInQueueEntryId() != null) {
            throw new IllegalArgumentException("Only one of bookingId or walkInQueueEntryId can be provided.");
        }

        if (request.bookingId() != null) {
            return createBookingReview(shopId, request, currentUserId);
        } else {
            return createWalkInReview(shopId, request, currentUserId);
        }
    }

    private ReviewResponse createBookingReview(UUID shopId, ReviewRequest request, UUID currentUserId) {
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found."));

        if (!booking.getShopId().equals(shopId)) {
            throw new ResourceNotFoundException("Booking not found.");
        }

        if (!booking.getCustomerId().equals(currentUserId)) {
            throw new IllegalArgumentException("You can only review your own bookings.");
        }

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new IllegalArgumentException("You can only review completed bookings.");
        }

        if (reviewRepository.existsByBookingId(request.bookingId())) {
            throw new IllegalArgumentException("You have already reviewed this booking.");
        }

        Review review = Review.builder()
                .shopId(shopId)
                .reviewerId(currentUserId)
                .bookingId(request.bookingId())
                .rating(request.rating())
                .comment(request.comment())
                .build();

        return toResponse(reviewRepository.save(review));
    }

    private ReviewResponse createWalkInReview(UUID shopId, ReviewRequest request, UUID currentUserId) {
        WalkInQueueEntry entry = walkInQueueEntryRepository.findById(request.walkInQueueEntryId())
                .orElseThrow(() -> new ResourceNotFoundException("Walk-in queue entry not found."));

        if (!entry.getShopId().equals(shopId)) {
            throw new ResourceNotFoundException("Walk-in queue entry not found.");
        }

        if (!currentUserId.equals(entry.getCustomerId())) {
            throw new IllegalArgumentException("You can only review your own walk-in visits.");
        }

        if (entry.getStatus() != WalkInStatus.COMPLETED) {
            throw new IllegalArgumentException("You can only review completed walk-in visits.");
        }

        if (reviewRepository.existsByWalkInQueueEntryId(request.walkInQueueEntryId())) {
            throw new IllegalArgumentException("You have already reviewed this walk-in visit.");
        }

        Review review = Review.builder()
                .shopId(shopId)
                .reviewerId(currentUserId)
                .walkInQueueEntryId(request.walkInQueueEntryId())
                .rating(request.rating())
                .comment(request.comment())
                .build();

        return toResponse(reviewRepository.save(review));
    }

    public List<ReviewResponse> listReviews(UUID shopId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Review> reviews = reviewRepository.findByShopId(shopId, pageable);
        return reviews.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public ShopRatingSummaryResponse getRatingSummary(UUID shopId) {
        Double avg = reviewRepository.findAverageRatingByShopId(shopId);
        long total = reviewRepository.countByShopId(shopId);
        return new ShopRatingSummaryResponse(avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0, total);
    }

    @Transactional
    public ReviewResponse replyToReview(UUID shopId, UUID reviewId, OwnerReplyRequest request, UUID currentUserId) {
        shopAccessService.verifyShopOwner(currentUserId, shopId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found."));

        if (!review.getShopId().equals(shopId)) {
            throw new ResourceNotFoundException("Review not found.");
        }

        if (review.getOwnerReply() != null) {
            throw new IllegalArgumentException("You have already replied to this review.");
        }

        review.setOwnerReply(request.reply());
        review.setOwnerRepliedAt(Instant.now());

        return toResponse(reviewRepository.save(review));
    }

    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getShopId(),
                review.getReviewerId(),
                review.getBookingId(),
                review.getWalkInQueueEntryId(),
                review.getRating(),
                review.getComment(),
                review.getOwnerReply(),
                review.getOwnerRepliedAt(),
                review.getCreatedAt()
        );
    }

}
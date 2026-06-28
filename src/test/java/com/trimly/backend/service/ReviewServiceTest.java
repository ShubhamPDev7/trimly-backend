package com.trimly.backend.service;

import com.trimly.backend.dto.review.OwnerReplyRequest;
import com.trimly.backend.dto.review.ReviewRequest;
import com.trimly.backend.dto.review.ReviewResponse;
import com.trimly.backend.dto.review.ShopRatingSummaryResponse;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.Review;
import com.trimly.backend.entity.Shop;
import com.trimly.backend.entity.User;
import com.trimly.backend.entity.WalkInQueueEntry;
import com.trimly.backend.enums.BookingStatus;
import com.trimly.backend.enums.WalkInStatus;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.ReviewRepository;
import com.trimly.backend.repository.ShopRepository;
import com.trimly.backend.repository.UserRepository;
import com.trimly.backend.repository.WalkInQueueEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private WalkInQueueEntryRepository walkInQueueEntryRepository;
    @Mock private ShopAccessService shopAccessService;
    @Mock private EmailService emailService;
    @Mock private UserRepository userRepository;
    @Mock private ShopRepository shopRepository;

    @InjectMocks
    private ReviewService reviewService;

    private UUID shopId;
    private UUID reviewerId;
    private UUID bookingId;
    private UUID entryId;
    private UUID ownerId;
    private Booking completedBooking;
    private WalkInQueueEntry completedEntry;
    private Shop shop;
    private User owner;
    private User reviewer;

    @BeforeEach
    void setUp() {
        shopId     = UUID.randomUUID();
        reviewerId = UUID.randomUUID();
        bookingId  = UUID.randomUUID();
        entryId    = UUID.randomUUID();
        ownerId    = UUID.randomUUID();

        completedBooking = Booking.builder()
                .id(bookingId)
                .shopId(shopId)
                .customerId(reviewerId)
                .status(BookingStatus.COMPLETED)
                .build();

        completedEntry = WalkInQueueEntry.builder()
                .id(entryId)
                .shopId(shopId)
                .customerId(reviewerId)
                .status(WalkInStatus.COMPLETED)
                .build();

        shop = Shop.builder()
                .id(shopId)
                .name("Trimly Barbershop")
                .ownerId(ownerId)
                .build();

        owner = new User();
        owner.setId(ownerId);
        owner.setName("Owner");
        owner.setEmail("owner@trimly.com");

        reviewer = new User();
        reviewer.setId(reviewerId);
        reviewer.setName("Shubham");
        reviewer.setEmail("shubham@test.com");
    }

    @Test
    void createReview_forBooking_succeeds() {
        ReviewRequest request = new ReviewRequest(5, "Excellent!", bookingId, null);
        Review savedReview = Review.builder()
                .id(UUID.randomUUID()).shopId(shopId).reviewerId(reviewerId)
                .bookingId(bookingId).rating(5).comment("Excellent!").createdAt(Instant.now())
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(completedBooking));
        when(reviewRepository.existsByBookingId(bookingId)).thenReturn(false);
        when(reviewRepository.save(any())).thenReturn(savedReview);
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));

        ReviewResponse response = reviewService.createReview(shopId, request, reviewerId);

        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.bookingId()).isEqualTo(bookingId);
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void createReview_neitherBookingNorWalkIn_throws() {
        ReviewRequest request = new ReviewRequest(4, "Good", null, null);

        assertThatThrownBy(() -> reviewService.createReview(shopId, request, reviewerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either bookingId or walkInQueueEntryId");
    }

    @Test
    void createReview_bothBookingAndWalkIn_throws() {
        ReviewRequest request = new ReviewRequest(4, "Good", bookingId, entryId);

        assertThatThrownBy(() -> reviewService.createReview(shopId, request, reviewerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only one of");
    }

    @Test
    void createReview_bookingNotCompleted_throws() {
        completedBooking.setStatus(BookingStatus.PENDING);
        ReviewRequest request = new ReviewRequest(4, "Good", bookingId, null);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(completedBooking));

        assertThatThrownBy(() -> reviewService.createReview(shopId, request, reviewerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completed bookings");
    }

    @Test
    void createReview_notCustomersOwnBooking_throws() {
        completedBooking.setCustomerId(UUID.randomUUID());
        ReviewRequest request = new ReviewRequest(4, "Good", bookingId, null);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(completedBooking));

        assertThatThrownBy(() -> reviewService.createReview(shopId, request, reviewerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("your own bookings");
    }

    @Test
    void createReview_duplicateBookingReview_throws() {
        ReviewRequest request = new ReviewRequest(5, "Love it", bookingId, null);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(completedBooking));
        when(reviewRepository.existsByBookingId(bookingId)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(shopId, request, reviewerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already reviewed");
    }

    @Test
    void createReview_forWalkIn_succeeds() {
        ReviewRequest request = new ReviewRequest(4, "Great service", null, entryId);
        Review savedReview = Review.builder()
                .id(UUID.randomUUID()).shopId(shopId).reviewerId(reviewerId)
                .walkInQueueEntryId(entryId).rating(4).comment("Great service").createdAt(Instant.now())
                .build();

        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(completedEntry));
        when(reviewRepository.existsByWalkInQueueEntryId(entryId)).thenReturn(false);
        when(reviewRepository.save(any())).thenReturn(savedReview);
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));

        ReviewResponse response = reviewService.createReview(shopId, request, reviewerId);

        assertThat(response.walkInQueueEntryId()).isEqualTo(entryId);
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void createReview_walkInNotCompleted_throws() {
        completedEntry.setStatus(WalkInStatus.WAITING);
        ReviewRequest request = new ReviewRequest(3, "Okay", null, entryId);

        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(completedEntry));

        assertThatThrownBy(() -> reviewService.createReview(shopId, request, reviewerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completed walk-in");
    }

    @Test
    void getRatingSummary_returnsAverageAndCount() {
        when(reviewRepository.findAverageRatingByShopId(shopId)).thenReturn(4.3);
        when(reviewRepository.countByShopId(shopId)).thenReturn(17L);

        ShopRatingSummaryResponse response = reviewService.getRatingSummary(shopId);

        assertThat(response.averageRating()).isEqualTo(4.3);
        assertThat(response.totalReviews()).isEqualTo(17L);
    }

    @Test
    void getRatingSummary_noReviews_returnsZero() {
        when(reviewRepository.findAverageRatingByShopId(shopId)).thenReturn(null);
        when(reviewRepository.countByShopId(shopId)).thenReturn(0L);

        ShopRatingSummaryResponse response = reviewService.getRatingSummary(shopId);

        assertThat(response.averageRating()).isEqualTo(0.0);
        assertThat(response.totalReviews()).isEqualTo(0L);
    }

    @Test
    void replyToReview_succeeds() {
        UUID reviewId = UUID.randomUUID();
        Review review = Review.builder()
                .id(reviewId).shopId(shopId).reviewerId(reviewerId)
                .rating(5).comment("Amazing").createdAt(Instant.now())
                .build();

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any())).thenReturn(review);
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));

        OwnerReplyRequest request = new OwnerReplyRequest("Thank you so much!");
        ReviewResponse response = reviewService.replyToReview(shopId, reviewId, request, ownerId);

        assertThat(review.getOwnerReply()).isEqualTo("Thank you so much!");
        assertThat(review.getOwnerRepliedAt()).isNotNull();
        verify(reviewRepository).save(review);
    }

    @Test
    void replyToReview_alreadyReplied_throws() {
        UUID reviewId = UUID.randomUUID();
        Review review = Review.builder()
                .id(reviewId).shopId(shopId).reviewerId(reviewerId)
                .rating(5).ownerReply("Already replied!").createdAt(Instant.now())
                .build();

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

        assertThatThrownBy(() ->
                reviewService.replyToReview(shopId, reviewId, new OwnerReplyRequest("Another reply"), ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already replied");
    }

    @Test
    void listReviews_returnsPaginatedResults() {
        Review r = Review.builder()
                .id(UUID.randomUUID()).shopId(shopId).reviewerId(reviewerId)
                .rating(4).comment("Nice").createdAt(Instant.now()).build();

        when(reviewRepository.findByShopId(eq(shopId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r)));

        List<ReviewResponse> result = reviewService.listReviews(shopId, 0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).shopId()).isEqualTo(shopId);
    }
}
package com.trimly.backend.controller;

import com.trimly.backend.dto.review.OwnerReplyRequest;
import com.trimly.backend.dto.review.ReviewRequest;
import com.trimly.backend.dto.review.ReviewResponse;
import com.trimly.backend.dto.review.ShopRatingSummaryResponse;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shops/{shopId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable UUID shopId,
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ReviewResponse response = reviewService.createReview(shopId, request, userDetails.getUser().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ReviewResponse>> listReviews(
            @PathVariable UUID shopId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<ReviewResponse> response = reviewService.listReviews(shopId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/summary")
    public ResponseEntity<ShopRatingSummaryResponse> getRatingSummary(
            @PathVariable UUID shopId
    ) {
        ShopRatingSummaryResponse response = reviewService.getRatingSummary(shopId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reviewId}/reply")
    public ResponseEntity<ReviewResponse> replyToReview(
            @PathVariable UUID shopId,
            @PathVariable UUID reviewId,
            @Valid @RequestBody OwnerReplyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ReviewResponse response = reviewService.replyToReview(shopId, reviewId, request, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

}
package com.trimly.backend.controller;

import com.trimly.backend.dto.bill.BillRequest;
import com.trimly.backend.dto.bill.BillResponse;
import com.trimly.backend.dto.booking.BookingRequest;
import com.trimly.backend.dto.booking.BookingResponse;
import com.trimly.backend.dto.booking.BookingStatusUpdateRequest;
import com.trimly.backend.enums.BookingStatus;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.BookingService;
import com.trimly.backend.service.BookingService.PagedBookingsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @PathVariable UUID shopId,
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        BookingResponse response = bookingService.createBooking(shopId, request, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{bookingId}/status")
    public ResponseEntity<BookingResponse> updateBookingStatus(
            @PathVariable UUID shopId,
            @PathVariable UUID bookingId,
            @Valid @RequestBody BookingStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        BookingResponse response = bookingService.updateBookingStatus(
                shopId, bookingId, request, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PagedBookingsResponse> listShopBookings(
            @PathVariable UUID shopId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        PagedBookingsResponse response = bookingService.listShopBookings(
                shopId, date, status, page, size, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{bookingId}/bill")
    public ResponseEntity<BillResponse> createBill(
            @PathVariable UUID shopId,
            @PathVariable UUID bookingId,
            @Valid @RequestBody BillRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        BillResponse response = bookingService.createBill(shopId, bookingId, request, userDetails.getUser().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{bookingId}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable UUID shopId,
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        BookingResponse response = bookingService.cancelBooking(shopId, bookingId, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

}
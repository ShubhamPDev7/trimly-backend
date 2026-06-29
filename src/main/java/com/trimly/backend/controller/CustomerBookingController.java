package com.trimly.backend.controller;

import com.trimly.backend.dto.booking.BookingResponse;
import com.trimly.backend.dto.customer.CustomerProfileResponse;
import com.trimly.backend.dto.customer.UpdateProfileRequest;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api/v1/customers/me")
@RequiredArgsConstructor
public class CustomerBookingController {

    private final CustomerService customerService;

    @GetMapping("/bookings")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(customerService.getMyBookings(userDetails.getUser().getId(), filter, page, size));
    }

    @PostMapping("/rebook-last")
    public ResponseEntity<BookingResponse> rebookLast(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(customerService.rebookLastService(userDetails.getUser(), date));
    }

    @GetMapping
    public ResponseEntity<CustomerProfileResponse> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(customerService.getMyProfile(userDetails.getUser()));
    }

    @PutMapping
    public ResponseEntity<CustomerProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(customerService.updateMyProfile(request, userDetails.getUser()));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteMyAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        customerService.deleteMyAccount(userDetails.getUser());
        return ResponseEntity.noContent().build();
    }
}
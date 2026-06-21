package com.trimly.backend.controller;

import com.trimly.backend.dto.booking.BookingResponse;
import com.trimly.backend.dto.customer.CustomerProfileResponse;
import com.trimly.backend.dto.customer.UpdateProfileRequest;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/me")
@RequiredArgsConstructor
public class CustomerBookingController {

    private final CustomerService customerService;

    @GetMapping("/bookings")
    public ResponseEntity<List<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(customerService.getMyBookings(userDetails.getUser().getId()));
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
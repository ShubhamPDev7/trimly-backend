package com.trimly.backend.controller;

import com.trimly.backend.dto.booking.BookingResponse;
import com.trimly.backend.dto.customer.CustomerProfileResponse;
import com.trimly.backend.dto.customer.UpdateProfileRequest;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.User;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.UserRepository;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.BookingMapper;
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

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;

    @GetMapping("/bookings")
    public ResponseEntity<List<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<Booking> bookings = bookingRepository.findByCustomerId(userDetails.getUser().getId());
        return ResponseEntity.ok(bookingMapper.toResponseList(bookings));
    }

    @GetMapping
    public ResponseEntity<CustomerProfileResponse> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        User user = userDetails.getUser();
        return ResponseEntity.ok(toProfileResponse(user));
    }

    @PutMapping
    public ResponseEntity<CustomerProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        User user = userDetails.getUser();
        user.setName(request.name());
        user.setPhone(request.phone());

        User updatedUser = userRepository.save(user);

        return ResponseEntity.ok(toProfileResponse(updatedUser));
    }

    private CustomerProfileResponse toProfileResponse(User user) {
        return new CustomerProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getCreatedAt()
        );
    }
}
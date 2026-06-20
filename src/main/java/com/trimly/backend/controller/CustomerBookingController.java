package com.trimly.backend.controller;

import com.trimly.backend.dto.booking.BookedServiceResponse;
import com.trimly.backend.dto.booking.BookingResponse;
import com.trimly.backend.dto.customer.CustomerProfileResponse;
import com.trimly.backend.dto.customer.UpdateProfileRequest;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.BookingServiceItem;
import com.trimly.backend.entity.ServiceItem;
import com.trimly.backend.entity.User;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.BookingServiceItemRepository;
import com.trimly.backend.repository.ServiceItemRepository;
import com.trimly.backend.repository.UserRepository;
import com.trimly.backend.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/customers/me")
@RequiredArgsConstructor
public class CustomerBookingController {

    private final BookingRepository bookingRepository;
    private final BookingServiceItemRepository bookingServiceItemRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final UserRepository userRepository;

    @GetMapping("/bookings")
    public ResponseEntity<List<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal CustomUserDetails userDetails
            ) {

        UUID customerId = userDetails.getUser().getId();
        List<Booking> bookings = bookingRepository.findByCustomerId(customerId);
        List<BookingResponse> response = bookings.stream()
                .map(this::toResponseWithServices)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);

    }

    private BookingResponse toResponseWithServices(Booking booking) {
        List<BookingServiceItem> bookingServices = bookingServiceItemRepository.findByBookingId(booking.getId());

        List<ServiceItem> services = serviceItemRepository.findAllById(
                bookingServices.stream().map(BookingServiceItem::getServiceId).collect(Collectors.toList())
        );

        Map<UUID, String> serviceNamesByid = services.stream()
                .collect(Collectors.toMap(ServiceItem::getId, ServiceItem::getName));

        List<BookedServiceResponse> serviceResponses = bookingServices.stream()
                .map(bs -> new BookedServiceResponse(
                        bs.getServiceId(),
                        serviceNamesByid.get(bs.getServiceId()),
                        bs.getPriceAtBooking()
                ))
                .collect(Collectors.toList());
        BigDecimal total = bookingServices.stream()
                .map(BookingServiceItem::getPriceAtBooking)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BookingResponse(
                booking.getId(),
                booking.getShopId(),
                booking.getCustomerId(),
                booking.getStaffId(),
                booking.getGuestName(),
                booking.getGuestPhone(),
                booking.getBookingDate(),
                booking.getTimeSlot(),
                booking.getStatus(),
                serviceResponses,
                total,
                booking.getCreatedAt()
        );
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

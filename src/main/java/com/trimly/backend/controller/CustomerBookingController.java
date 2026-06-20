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

        if (bookings.isEmpty()) return ResponseEntity.ok(List.of());


        List<UUID> bookingIds = bookings.stream().map(Booking::getId).collect(Collectors.toList());
        List<BookingServiceItem> allItems = bookingServiceItemRepository.findByBookingIdIn(bookingIds);


        List<UUID> serviceIds = allItems.stream().map(BookingServiceItem::getServiceId).collect(Collectors.toList());
        List<ServiceItem> allServices = serviceItemRepository.findAllById(serviceIds);


        Map<UUID, List<BookingServiceItem>> itemsByBookingId = allItems.stream()
                .collect(Collectors.groupingBy(BookingServiceItem::getBookingId));
        Map<UUID, String> serviceNamesById = allServices.stream()
                .collect(Collectors.toMap(ServiceItem::getId, ServiceItem::getName));
        Map<UUID, BigDecimal> servicePricesById = allServices.stream()
                .collect(Collectors.toMap(ServiceItem::getId, ServiceItem::getPrice));


        List<BookingResponse> response = bookings.stream()
                .map(booking -> {
                    List<BookingServiceItem> items = itemsByBookingId.getOrDefault(booking.getId(), List.of());

                    List<BookedServiceResponse> serviceResponses = items.stream()
                            .map(bs -> new BookedServiceResponse(
                                    bs.getServiceId(),
                                    serviceNamesById.get(bs.getServiceId()),
                                    bs.getPriceAtBooking()
                            ))
                            .collect(Collectors.toList());

                    BigDecimal total = items.stream()
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
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
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
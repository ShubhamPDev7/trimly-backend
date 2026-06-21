package com.trimly.backend.controller;

import com.trimly.backend.dto.bill.BillRequest;
import com.trimly.backend.dto.bill.BillResponse;
import com.trimly.backend.dto.booking.BookedServiceResponse;
import com.trimly.backend.dto.booking.BookingRequest;
import com.trimly.backend.dto.booking.BookingResponse;
import com.trimly.backend.dto.booking.BookingStatusUpdateRequest;
import com.trimly.backend.entity.*;
import com.trimly.backend.enums.BookingStatus;
import com.trimly.backend.enums.PaymentStatus;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.BillRepository;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.BookingServiceItemRepository;
import com.trimly.backend.repository.ServiceItemRepository;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.ShopAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shops/{shopId}/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingRepository bookingRepository;
    private final BookingServiceItemRepository bookingServiceItemRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final ShopAccessService shopAccessService;
    private final BillRepository billRepository;


    @Transactional
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @PathVariable UUID shopId,
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        User currentUser = userDetails.getUser();
        boolean isStaff = shopAccessService.hasShopAccess(currentUser.getId(), shopId);

        UUID customerId;
        String guestName;
        String guestPhone;

        if (isStaff) {
            if (request.guestName() == null || request.guestName().isBlank()
                    || request.guestPhone() == null || request.guestPhone().isBlank()) {
                throw new IllegalArgumentException(
                        "guestName and guestPhone are required when staff creates a booking on behalf of a customer.");
            }
            customerId = null;
            guestName = request.guestName();
            guestPhone = request.guestPhone();
        } else {
            customerId = currentUser.getId();
            guestName = null;
            guestPhone = null;
        }

        List<ServiceItem> services = serviceItemRepository.findAllById(request.serviceIds());

        if (services.size() != request.serviceIds().size()) {
            throw new IllegalArgumentException("One or more services were not found.");
        }

        boolean staffExists = shopAccessService.hasShopAccess(request.staffId(), shopId);
        if (!staffExists) {
            throw new IllegalArgumentException("The specified staff member does not belong to this shop.");
        }

        List<Booking> existingBookings = bookingRepository.findByStaffIdAndBookingDate(request.staffId(), request.bookingDate());

        boolean slotTaken = existingBookings.stream()
                .anyMatch(b -> b.getTimeSlot().equals(request.timeSlot())
                        && b.getStatus() != BookingStatus.REJECTED
                        && b.getStatus() != BookingStatus.CANCELLED);

        if (slotTaken) {
            throw new IllegalArgumentException("This time slot is already booked for the selected staff member.");
        }

        for (ServiceItem service : services) {
            if (!service.getShopId().equals(shopId)) {
                throw new IllegalArgumentException("One or more services do not belong to this shop.");
            }
        }

        Booking booking = Booking.builder()
                .shopId(shopId)
                .customerId(customerId)
                .staffId(request.staffId())
                .guestName(guestName)
                .guestPhone(guestPhone)
                .bookingDate(request.bookingDate())
                .timeSlot(request.timeSlot())
                .status(BookingStatus.PENDING)
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        List<BookingServiceItem> bookingServices = services.stream()
                .map(service -> BookingServiceItem.builder()
                        .bookingId(savedBooking.getId())
                        .serviceId(service.getId())
                        .priceAtBooking(service.getPrice())
                        .build())
                .collect(Collectors.toList());

        bookingServiceItemRepository.saveAll(bookingServices);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(savedBooking, bookingServices, services));
    }

    private BookingResponse toResponse(Booking booking, List<BookingServiceItem> bookingServices, List<ServiceItem> services) {
        Map<UUID, String> serviceNamesById = services.stream()
                .collect(Collectors.toMap(ServiceItem::getId, ServiceItem::getName));

        List<BookedServiceResponse> serviceResponses = bookingServices.stream()
                .map(bs -> new BookedServiceResponse(
                        bs.getServiceId(),
                        serviceNamesById.get(bs.getServiceId()),
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

    @Transactional
    @PatchMapping("/{bookingId}/status")
    public ResponseEntity<BookingResponse> updateBookingStatus(
            @PathVariable UUID shopId,
            @PathVariable UUID bookingId,
            @Valid @RequestBody BookingStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        shopAccessService.verifyShopAccess(userDetails.getUser().getId(), shopId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found."));

        if (!booking.getShopId().equals(shopId)) {
            throw new ResourceNotFoundException("Booking not found.");
        }

        if (!booking.getStatus().canTransitionTo(request.status())) {
            throw new IllegalArgumentException(
                    "Cannot transition booking from " + booking.getStatus() + " to " + request.status() + ".");
        }

        booking.setStatus(request.status());
        Booking updatedBooking = bookingRepository.save(booking);

        List<BookingServiceItem> bookingService = bookingServiceItemRepository.findByBookingId(bookingId);
        List<ServiceItem> services = serviceItemRepository.findAllById(
                bookingService.stream().map(BookingServiceItem::getServiceId).collect(Collectors.toList())
        );

        return ResponseEntity.ok(toResponse(updatedBooking, bookingService, services));
    }

    @GetMapping
    public ResponseEntity<List<BookingResponse>> listShopBookings(
            @PathVariable UUID shopId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) BookingStatus status,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        shopAccessService.verifyShopAccess(userDetails.getUser().getId(), shopId);

        List<Booking> bookings = bookingRepository.findByShopId(shopId);

        List<Booking> filtered = bookings.stream()
                .filter(b -> date == null || b.getBookingDate().equals(date))
                .filter(b -> status == null || b.getStatus() == status)
                .collect(Collectors.toList());

        return ResponseEntity.ok(toResponseList(filtered));
    }

    private List<BookingResponse> toResponseList(List<Booking> bookings) {
        if (bookings.isEmpty()) return List.of();


        List<UUID> bookingIds = bookings.stream().map(Booking::getId).collect(Collectors.toList());
        List<BookingServiceItem> allItems = bookingServiceItemRepository.findByBookingIdIn(bookingIds);


        List<UUID> serviceIds = allItems.stream().map(BookingServiceItem::getServiceId).collect(Collectors.toList());
        List<ServiceItem> allServices = serviceItemRepository.findAllById(serviceIds);


        Map<UUID, List<BookingServiceItem>> itemsByBookingId = allItems.stream()
                .collect(Collectors.groupingBy(BookingServiceItem::getBookingId));
        Map<UUID, ServiceItem> servicesById = allServices.stream()
                .collect(Collectors.toMap(ServiceItem::getId, s -> s));


        return bookings.stream()
                .map(booking -> {
                    List<BookingServiceItem> items = itemsByBookingId.getOrDefault(booking.getId(), List.of());
                    List<ServiceItem> services = items.stream()
                            .map(i -> servicesById.get(i.getServiceId()))
                            .collect(Collectors.toList());
                    return toResponse(booking, items, services);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    @PostMapping("/{bookingId}/bill")
    public ResponseEntity<BillResponse> createBill(
            @PathVariable UUID shopId,
            @PathVariable UUID bookingId,
            @Valid @RequestBody BillRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        shopAccessService.verifyShopAccess(userDetails.getUser().getId(), shopId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found."));

        if (!booking.getShopId().equals(shopId)) {
            throw new ResourceNotFoundException("Booking not found.");
        }

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new IllegalArgumentException("Only completed bookings can be billed.");
        }

        if (billRepository.findByBookingId(bookingId).isPresent()) {
            throw new IllegalArgumentException("This booking has already been billed.");
        }

        List<BookingServiceItem> bookingServices = bookingServiceItemRepository.findByBookingId(bookingId);

        BigDecimal total = bookingServices.stream()
                .map(BookingServiceItem::getPriceAtBooking)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Bill bill = Bill.builder()
                .shopId(shopId)
                .bookingId(bookingId)
                .totalAmount(total)
                .paymentMode(request.paymentMode())
                .paymentStatus(PaymentStatus.PAID)
                .build();

        Bill savedBill = billRepository.save(bill);

        return ResponseEntity.status(HttpStatus.CREATED).body(toBillResponse(savedBill));
    }

    private BillResponse toBillResponse(Bill bill) {
        return new BillResponse(
                bill.getId(),
                bill.getShopId(),
                bill.getBookingId(),
                bill.getTotalAmount(),
                bill.getPaymentMode(),
                bill.getPaymentStatus(),
                bill.getCreatedAt()
        );
    }

}
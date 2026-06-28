package com.trimly.backend.service;

import com.trimly.backend.dto.bill.BillRequest;
import com.trimly.backend.dto.bill.BillResponse;
import com.trimly.backend.dto.booking.AvailableSlotsResponse;
import com.trimly.backend.dto.booking.BookingRequest;
import com.trimly.backend.dto.booking.BookingResponse;
import com.trimly.backend.dto.booking.BookingStatusUpdateRequest;
import com.trimly.backend.entity.Bill;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.BookingServiceItem;
import com.trimly.backend.entity.ServiceItem;
import com.trimly.backend.entity.User;
import com.trimly.backend.enums.BookingStatus;
import com.trimly.backend.enums.PaymentStatus;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.BillRepository;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.BookingServiceItemRepository;
import com.trimly.backend.repository.ServiceItemRepository;
import com.trimly.backend.repository.ShopClosedDateRepository;
import com.trimly.backend.repository.ShopHoursRepository;
import com.trimly.backend.repository.ShopRepository;
import org.springframework.dao.DataIntegrityViolationException;
import com.trimly.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingServiceItemRepository bookingServiceItemRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final ShopAccessService shopAccessService;
    private final BillRepository billRepository;
    private final BookingMapper bookingMapper;
    private final LoyaltyService loyaltyService;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ShopHoursRepository shopHoursRepository;
    private final ShopClosedDateRepository shopClosedDateRepository;

    @Transactional
    public BookingResponse createBooking(UUID shopId, BookingRequest request, User currentUser) {
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

        Booking savedBooking;
        try {
            savedBooking = bookingRepository.save(booking);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("This time slot is no longer available. Please choose a different time.");
        }

        List<BookingServiceItem> bookingServices = services.stream()
                .map(service -> BookingServiceItem.builder()
                        .bookingId(savedBooking.getId())
                        .serviceId(service.getId())
                        .priceAtBooking(service.getPrice())
                        .build())
                .collect(Collectors.toList());

        bookingServiceItemRepository.saveAll(bookingServices);

        // Email notifications — customer only (not guest bookings)
        if (customerId != null) {
            String serviceNames = services.stream()
                    .map(ServiceItem::getName)
                    .collect(Collectors.joining(", "));
            try {
                User customer = userRepository.findById(customerId).orElseThrow();
                emailService.sendBookingConfirmationToCustomer(
                        customer.getEmail(), customer.getName(),
                        shopRepository.findById(shopId).map(s -> s.getName()).orElse("the shop"),
                        request.bookingDate(), request.timeSlot(), serviceNames);
            } catch (Exception e) {
                log.error("Failed to send booking confirmation to customer: {}", e.getMessage());
            }
            try {
                var shop = shopRepository.findById(shopId).orElseThrow();
                User owner = userRepository.findById(shop.getOwnerId()).orElseThrow();
                User customer = userRepository.findById(customerId).orElseThrow();
                emailService.sendNewBookingToOwner(
                        owner.getEmail(), owner.getName(), customer.getName(),
                        shop.getName(), request.bookingDate(), request.timeSlot(), serviceNames);
            } catch (Exception e) {
                log.error("Failed to send new booking notification to owner: {}", e.getMessage());
            }
        }

        return bookingMapper.toResponse(savedBooking, bookingServices, services);
    }

    @Transactional
    public BookingResponse updateBookingStatus(UUID shopId, UUID bookingId, BookingStatusUpdateRequest request, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

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

        // Notify customer on accept/reject — only for registered customers
        if (updatedBooking.getCustomerId() != null) {
            try {
                User customer = userRepository.findById(updatedBooking.getCustomerId()).orElseThrow();
                String shopName = shopRepository.findById(shopId).map(s -> s.getName()).orElse("the shop");
                if (request.status() == BookingStatus.ACCEPTED) {
                    emailService.sendBookingAcceptedToCustomer(
                            customer.getEmail(), customer.getName(), shopName,
                            updatedBooking.getBookingDate(), updatedBooking.getTimeSlot());
                } else if (request.status() == BookingStatus.REJECTED) {
                    emailService.sendBookingRejectedToCustomer(
                            customer.getEmail(), customer.getName(), shopName,
                            updatedBooking.getBookingDate(), updatedBooking.getTimeSlot());
                }
            } catch (Exception e) {
                log.error("Failed to send booking status email to customer: {}", e.getMessage());
            }
        }

        return bookingMapper.toResponse(updatedBooking);
    }

    public PagedBookingsResponse listShopBookings(UUID shopId, LocalDate date, BookingStatus status, int page, int size, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        Pageable pageable = PageRequest.of(page, size,
                Sort.by("bookingDate").descending().and(Sort.by("timeSlot").descending()));

        Page<Booking> bookingPage;
        if (date != null && status != null) {
            bookingPage = bookingRepository.findByShopIdAndBookingDateAndStatus(shopId, date, status, pageable);
        } else if (date != null) {
            bookingPage = bookingRepository.findByShopIdAndBookingDate(shopId, date, pageable);
        } else if (status != null) {
            bookingPage = bookingRepository.findByShopIdAndStatus(shopId, status, pageable);
        } else {
            bookingPage = bookingRepository.findByShopId(shopId, pageable);
        }

        return new PagedBookingsResponse(
                bookingMapper.toResponseList(bookingPage.getContent()),
                bookingPage.getNumber(),
                bookingPage.getSize(),
                bookingPage.getTotalElements(),
                bookingPage.getTotalPages(),
                bookingPage.isLast()
        );
    }

    public record PagedBookingsResponse(
            List<BookingResponse> bookings,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean last
    ) {}

    @Transactional
    public BillResponse createBill(UUID shopId, UUID bookingId, BillRequest request, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

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

        loyaltyService.awardPoints(shopId, booking.getCustomerId(), savedBill.getId(), total);
        return toBillResponse(savedBill);

    }

    private static final int SLOT_INTERVAL_MINUTES = 30;

    @Transactional(readOnly = true)
    public AvailableSlotsResponse getAvailableSlots(UUID shopId, LocalDate date, UUID staffId) {
        if (!shopRepository.existsById(shopId)) {
            throw new ResourceNotFoundException("Shop not found.");
        }

        if (!shopAccessService.hasShopAccess(staffId, shopId)) {
            throw new IllegalArgumentException("The specified staff member does not belong to this shop.");
        }

        if (shopClosedDateRepository.existsByShopIdAndClosedDate(shopId, date)) {
            return new AvailableSlotsResponse(shopId, staffId, date, SLOT_INTERVAL_MINUTES, List.of());
        }

        int dayOfWeek = date.getDayOfWeek().getValue();
        var hours = shopHoursRepository.findByShopIdAndDayOfWeek(shopId, dayOfWeek)
                .orElse(null);

        if (hours == null || hours.isClosed()) {
            return new AvailableSlotsResponse(shopId, staffId, date, SLOT_INTERVAL_MINUTES, List.of());
        }

        Set<LocalTime> takenSlots = bookingRepository
                .findByStaffIdAndBookingDate(staffId, date)
                .stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED
                        && b.getStatus() != BookingStatus.REJECTED)
                .map(Booking::getTimeSlot)
                .collect(Collectors.toSet());

        List<LocalTime> available = new ArrayList<>();
        LocalTime cursor = hours.getOpenTime();
        LocalTime closeTime = hours.getCloseTime();

        while (cursor.isBefore(closeTime)) {
            if (!takenSlots.contains(cursor)) {
                available.add(cursor);
            }
            cursor = cursor.plusMinutes(SLOT_INTERVAL_MINUTES);
        }

        return new AvailableSlotsResponse(shopId, staffId, date, SLOT_INTERVAL_MINUTES, available);
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

    @Transactional
    public BookingResponse cancelBooking(UUID shopId, UUID bookingId, UUID currentUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found."));

        if (!booking.getShopId().equals(shopId)) {
            throw new ResourceNotFoundException("Booking not found.");
        }

        if (!booking.getCustomerId().equals(currentUserId)) {
            throw new IllegalArgumentException("You can only cancel your own bookings.");
        }

        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new IllegalArgumentException("Only pending or accepted bookings can be cancelled.");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking updated = bookingRepository.save(booking);


        try {
            User customer = userRepository.findById(currentUserId).orElseThrow();
            var shop = shopRepository.findById(shopId).orElseThrow();
            emailService.sendBookingCancelledToCustomer(
                    customer.getEmail(), customer.getName(), shop.getName(),
                    updated.getBookingDate(), updated.getTimeSlot());
            User owner = userRepository.findById(shop.getOwnerId()).orElseThrow();
            emailService.sendBookingCancelledToOwner(
                    owner.getEmail(), owner.getName(), customer.getName(),
                    shop.getName(), updated.getBookingDate(), updated.getTimeSlot());
        } catch (Exception e) {
            log.error("Failed to send cancellation emails: {}", e.getMessage());
        }

        return bookingMapper.toResponse(updated);
    }

}
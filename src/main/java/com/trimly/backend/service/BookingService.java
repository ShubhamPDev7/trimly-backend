package com.trimly.backend.service;

import com.trimly.backend.dto.bill.BillRequest;
import com.trimly.backend.dto.bill.BillResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingServiceItemRepository bookingServiceItemRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final ShopAccessService shopAccessService;
    private final BillRepository billRepository;
    private final BookingMapper bookingMapper;

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

        Booking savedBooking = bookingRepository.save(booking);

        List<BookingServiceItem> bookingServices = services.stream()
                .map(service -> BookingServiceItem.builder()
                        .bookingId(savedBooking.getId())
                        .serviceId(service.getId())
                        .priceAtBooking(service.getPrice())
                        .build())
                .collect(Collectors.toList());

        bookingServiceItemRepository.saveAll(bookingServices);

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

        return bookingMapper.toResponse(updatedBooking);
    }

    public List<BookingResponse> listShopBookings(UUID shopId, LocalDate date, BookingStatus status, int page, int size, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        Pageable pageable = PageRequest.of(page, size);
        List<Booking> bookings = bookingRepository.findByShopId(shopId, pageable).getContent();

        List<Booking> filtered = bookings.stream()
                .filter(b -> date == null || b.getBookingDate().equals(date))
                .filter(b -> status == null || b.getStatus() == status)
                .collect(Collectors.toList());

        return bookingMapper.toResponseList(filtered);
    }

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

        return toBillResponse(savedBill);
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
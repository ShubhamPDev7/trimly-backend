package com.trimly.backend.service;

import com.trimly.backend.dto.booking.BookingRequest;
import com.trimly.backend.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.trimly.backend.dto.booking.BookingResponse;
import com.trimly.backend.dto.customer.CustomerProfileResponse;
import com.trimly.backend.dto.customer.UpdateProfileRequest;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.BookingServiceItem;
import com.trimly.backend.entity.ServiceRecord;
import com.trimly.backend.entity.User;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.BookingServiceItemRepository;
import com.trimly.backend.repository.ServiceRecordRepository;
import com.trimly.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final BookingRepository bookingRepository;
    private final BookingServiceItemRepository bookingServiceItemRepository;
    private final ServiceRecordRepository serviceRecordRepository;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;
    private final BookingService bookingService;

    public Page<BookingResponse> getMyBookings(UUID customerId, String filter, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("bookingDate").descending().and(Sort.by("timeSlot").descending()));
        LocalDate today = LocalDate.now();

        Page<Booking> bookings;
        if ("upcoming".equalsIgnoreCase(filter)) {
            bookings = bookingRepository.findByCustomerIdAndBookingDateGreaterThanEqualAndStatusIn(
                    customerId, today,
                    List.of(BookingStatus.PENDING, BookingStatus.ACCEPTED),
                    pageable);
        } else if ("past".equalsIgnoreCase(filter)) {
            bookings = bookingRepository.findByCustomerIdAndBookingDateLessThan(customerId, today, pageable);
        } else if ("cancelled".equalsIgnoreCase(filter)) {
            bookings = bookingRepository.findByCustomerIdAndStatus(customerId, BookingStatus.CANCELLED, pageable);
        } else {
            bookings = bookingRepository.findByCustomerId(customerId, pageable);
        }
        return bookings.map(bookingMapper::toResponse);
    }

    public BookingResponse rebookLastService(User currentUser, LocalDate newDate) {
        ServiceRecord last = serviceRecordRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No previous service record found."));

        if (last.getBookingId() == null) {
            throw new IllegalStateException("Last service was a walk-in — cannot rebook automatically.");
        }

        Booking original = bookingRepository.findById(last.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Original booking not found."));

        List<UUID> serviceIds = bookingServiceItemRepository.findByBookingId(original.getId())
                .stream()
                .map(BookingServiceItem::getServiceId)
                .toList();

        BookingRequest request = new BookingRequest(
                original.getStaffId(),
                newDate,
                original.getTimeSlot(),
                serviceIds,
                null,
                null
        );

        return bookingService.createBooking(original.getShopId(), request, currentUser);
    }

    public CustomerProfileResponse getMyProfile(User user) {
        return toProfileResponse(user);
    }

    public CustomerProfileResponse updateMyProfile(UpdateProfileRequest request, User user) {
        user.setName(request.name());
        if (request.phone() != null) user.setPhone(request.phone());
        if (request.email() != null) user.setEmail(request.email());
        User updatedUser = userRepository.save(user);
        return toProfileResponse(updatedUser);
    }

    public void deleteMyAccount(User user) {
        user.setDeleted(true);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
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
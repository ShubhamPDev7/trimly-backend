package com.trimly.backend.service;

import com.trimly.backend.dto.booking.BookingResponse;
import com.trimly.backend.dto.customer.CustomerProfileResponse;
import com.trimly.backend.dto.customer.UpdateProfileRequest;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.User;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;

    public List<BookingResponse> getMyBookings(UUID customerId) {
        List<Booking> bookings = bookingRepository.findByCustomerId(customerId);
        return bookingMapper.toResponseList(bookings);
    }

    public CustomerProfileResponse getMyProfile(User user) {
        return toProfileResponse(user);
    }

    public CustomerProfileResponse updateMyProfile(UpdateProfileRequest request, User user) {
        user.setName(request.name());
        user.setPhone(request.phone());
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
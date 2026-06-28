package com.trimly.backend.service;

import com.trimly.backend.dto.booking.BookingResponse;
import com.trimly.backend.dto.customer.CustomerProfileResponse;
import com.trimly.backend.dto.customer.UpdateProfileRequest;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.BookingServiceItem;
import com.trimly.backend.entity.ServiceRecord;
import com.trimly.backend.entity.User;
import com.trimly.backend.enums.BookingStatus;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.BookingServiceItemRepository;
import com.trimly.backend.repository.ServiceRecordRepository;
import com.trimly.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingServiceItemRepository bookingServiceItemRepository;
    @Mock private ServiceRecordRepository serviceRecordRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookingMapper bookingMapper;
    @Mock private BookingService bookingService;

    @InjectMocks
    private CustomerService customerService;

    private User testUser;
    private UUID shopId;
    private UUID bookingId;
    private UUID staffId;

    @BeforeEach
    void setUp() {
        shopId    = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        staffId   = UUID.randomUUID();

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setName("Shubham");
        testUser.setEmail("shubham@test.com");
        testUser.setPhone("9999999999");
    }

    @Test
    void getMyProfile_returnsCorrectResponse() {
        CustomerProfileResponse response = customerService.getMyProfile(testUser);

        assertThat(response.name()).isEqualTo("Shubham");
        assertThat(response.email()).isEqualTo("shubham@test.com");
        assertThat(response.phone()).isEqualTo("9999999999");
    }

    @Test
    void updateMyProfile_updatesNameAndPhone() {
        UpdateProfileRequest request = new UpdateProfileRequest("New Name", "8888888888");
        when(userRepository.save(testUser)).thenReturn(testUser);

        CustomerProfileResponse response = customerService.updateMyProfile(request, testUser);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.phone()).isEqualTo("8888888888");
        verify(userRepository).save(testUser);
    }

    @Test
    void deleteMyAccount_setsDeletedTrueAndSaves() {
        when(userRepository.save(testUser)).thenReturn(testUser);

        customerService.deleteMyAccount(testUser);

        assertThat(testUser.isDeleted()).isTrue();
        assertThat(testUser.getDeletedAt()).isNotNull();
        verify(userRepository).save(testUser);
    }

    @Test
    void getMyBookings_returnsBookingList() {
        Booking booking = Booking.builder().id(bookingId).shopId(shopId).build();
        BookingResponse mockResponse = mock(BookingResponse.class);

        when(bookingRepository.findByCustomerId(testUser.getId()))
                .thenReturn(List.of(booking));
        when(bookingMapper.toResponseList(List.of(booking)))
                .thenReturn(List.of(mockResponse));

        List<BookingResponse> result = customerService.getMyBookings(testUser.getId());

        assertThat(result).hasSize(1);
        verify(bookingRepository).findByCustomerId(testUser.getId());
    }

    @Test
    void rebookLastService_success() {
        LocalDate newDate = LocalDate.now().plusDays(3);

        ServiceRecord lastRecord = ServiceRecord.builder()
                .id(UUID.randomUUID())
                .bookingId(bookingId)
                .customerId(testUser.getId())
                .build();

        Booking originalBooking = Booking.builder()
                .id(bookingId)
                .shopId(shopId)
                .staffId(staffId)
                .timeSlot(LocalTime.of(11, 0))
                .status(BookingStatus.COMPLETED)
                .build();

        BookingServiceItem item = BookingServiceItem.builder()
                .serviceId(UUID.randomUUID())
                .build();

        BookingResponse mockResponse = mock(BookingResponse.class);

        when(serviceRecordRepository.findTopByCustomerIdOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(Optional.of(lastRecord));
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(originalBooking));
        when(bookingServiceItemRepository.findByBookingId(bookingId))
                .thenReturn(List.of(item));
        when(bookingService.createBooking(eq(shopId), any(), eq(testUser)))
                .thenReturn(mockResponse);

        BookingResponse result = customerService.rebookLastService(testUser, newDate);

        assertThat(result).isEqualTo(mockResponse);
        verify(bookingService).createBooking(eq(shopId), any(), eq(testUser));
    }

    @Test
    void rebookLastService_noServiceRecord_throws() {
        when(serviceRecordRepository.findTopByCustomerIdOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                customerService.rebookLastService(testUser, LocalDate.now().plusDays(1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No previous service record found");
    }

    @Test
    void rebookLastService_lastWasWalkIn_throws() {
        ServiceRecord walkInRecord = ServiceRecord.builder()
                .id(UUID.randomUUID())
                .bookingId(null)
                .walkInQueueEntryId(UUID.randomUUID())
                .customerId(testUser.getId())
                .build();

        when(serviceRecordRepository.findTopByCustomerIdOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(Optional.of(walkInRecord));

        assertThatThrownBy(() ->
                customerService.rebookLastService(testUser, LocalDate.now().plusDays(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("walk-in");
    }
}
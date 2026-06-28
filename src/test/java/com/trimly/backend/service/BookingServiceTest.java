package com.trimly.backend.service;

import com.trimly.backend.dto.booking.BookingRequest;
import com.trimly.backend.dto.booking.BookingStatusUpdateRequest;
import com.trimly.backend.dto.booking.BookingResponse;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.ServiceItem;
import com.trimly.backend.entity.User;
import com.trimly.backend.enums.BookingStatus;
import com.trimly.backend.enums.ServiceCategory;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.BillRepository;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.BookingServiceItemRepository;
import com.trimly.backend.repository.ServiceItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingServiceItemRepository bookingServiceItemRepository;
    @Mock private ServiceItemRepository serviceItemRepository;
    @Mock private ShopAccessService shopAccessService;
    @Mock private BillRepository billRepository;
    @Mock private BookingMapper bookingMapper;

    @InjectMocks
    private BookingService bookingService;

    private UUID shopId;
    private UUID staffId;
    private UUID bookingId;
    private UUID currentUserId;
    private User staffUser;
    private Booking booking;
    private ServiceItem serviceItem;

    @BeforeEach
    void setUp() {
        shopId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();

        staffUser = new User();
        staffUser.setId(staffId);
        staffUser.setName("Staff One");

        booking = Booking.builder()
                .id(bookingId)
                .shopId(shopId)
                .staffId(staffId)
                .bookingDate(LocalDate.now())
                .timeSlot(LocalTime.of(10, 0))
                .status(BookingStatus.PENDING)
                .build();

        serviceItem = ServiceItem.builder()
                .id(UUID.randomUUID())
                .shopId(shopId)
                .name("Haircut")
                .price(new BigDecimal("200.00"))
                .category(ServiceCategory.MALE)
                .build();
    }

    @Test
    void updateBookingStatus_fromPendingToAccepted_succeeds() {
        BookingStatusUpdateRequest request = new BookingStatusUpdateRequest(BookingStatus.ACCEPTED);
        BookingResponse mockResponse = mock(BookingResponse.class);

        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(bookingMapper.toResponse(booking)).thenReturn(mockResponse);

        BookingResponse response = bookingService.updateBookingStatus(shopId, bookingId, request, currentUserId);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ACCEPTED);
        assertThat(response).isEqualTo(mockResponse);
        verify(bookingRepository).save(booking);
    }

    @Test
    void updateBookingStatus_invalidTransition_throwsException() {
        booking.setStatus(BookingStatus.COMPLETED);
        BookingStatusUpdateRequest request = new BookingStatusUpdateRequest(BookingStatus.PENDING);

        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.updateBookingStatus(shopId, bookingId, request, currentUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot transition booking");
    }

    @Test
    void updateBookingStatus_bookingNotFound_throwsException() {
        BookingStatusUpdateRequest request = new BookingStatusUpdateRequest(BookingStatus.ACCEPTED);

        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.updateBookingStatus(shopId, bookingId, request, currentUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Booking not found");
    }

    @Test
    void updateBookingStatus_bookingBelongsToDifferentShop_throwsException() {
        booking.setShopId(UUID.randomUUID());
        BookingStatusUpdateRequest request = new BookingStatusUpdateRequest(BookingStatus.ACCEPTED);

        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.updateBookingStatus(shopId, bookingId, request, currentUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createBill_bookingNotCompleted_throwsException() {
        booking.setStatus(BookingStatus.ACCEPTED);

        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.createBill(shopId, bookingId, null, currentUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only completed bookings can be billed");
    }

    @Test
    void createBill_alreadyBilled_throwsException() {
        booking.setStatus(BookingStatus.COMPLETED);

        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(billRepository.findByBookingId(bookingId)).thenReturn(Optional.of(mock(com.trimly.backend.entity.Bill.class)));

        assertThatThrownBy(() -> bookingService.createBill(shopId, bookingId, null, currentUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been billed");
    }

    @Test
    void createBooking_asCustomer_setsCustomerIdAndNoGuestInfo() {
        UUID serviceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        User customer = new User();
        customer.setId(customerId);

        BookingRequest request = new BookingRequest(
                staffId, LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                List.of(serviceId), null, null
        );

        serviceItem.setId(serviceId);

        when(shopAccessService.hasShopAccess(customerId, shopId)).thenReturn(false);
        when(serviceItemRepository.findAllById(List.of(serviceId))).thenReturn(List.of(serviceItem));
        when(shopAccessService.hasShopAccess(staffId, shopId)).thenReturn(true);
        when(bookingRepository.findByStaffIdAndBookingDate(staffId, request.bookingDate())).thenReturn(List.of());
        when(bookingRepository.save(any())).thenReturn(booking);
        when(bookingServiceItemRepository.saveAll(any())).thenReturn(List.of());
        when(bookingMapper.toResponse(any(), any(), any())).thenReturn(mock(BookingResponse.class));

        BookingResponse response = bookingService.createBooking(shopId, request, customer);

        assertThat(response).isNotNull();
        verify(bookingRepository).save(any());
    }

    @Test
    void createBooking_asStaff_withoutGuestInfo_throwsException() {
        User staffUser = new User();
        staffUser.setId(staffId);

        BookingRequest request = new BookingRequest(
                staffId, LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                List.of(UUID.randomUUID()), null, null
        );

        when(shopAccessService.hasShopAccess(staffId, shopId)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBooking(shopId, request, staffUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("guestName and guestPhone are required");
    }

    @Test
    void createBooking_slotAlreadyTaken_throwsException() {
        UUID serviceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        User customer = new User();
        customer.setId(customerId);

        BookingRequest request = new BookingRequest(
                staffId, LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                List.of(serviceId), null, null
        );

        Booking existingBooking = Booking.builder()
                .staffId(staffId)
                .bookingDate(request.bookingDate())
                .timeSlot(LocalTime.of(10, 0))
                .status(BookingStatus.ACCEPTED)
                .build();

        serviceItem.setId(serviceId);

        when(shopAccessService.hasShopAccess(customerId, shopId)).thenReturn(false);
        when(serviceItemRepository.findAllById(List.of(serviceId))).thenReturn(List.of(serviceItem));
        when(shopAccessService.hasShopAccess(staffId, shopId)).thenReturn(true);
        when(bookingRepository.findByStaffIdAndBookingDate(staffId, request.bookingDate())).thenReturn(List.of(existingBooking));

        assertThatThrownBy(() -> bookingService.createBooking(shopId, request, customer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("time slot is already booked");
    }

    @Test
    void createBooking_serviceNotFound_throwsException() {
        UUID serviceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        User customer = new User();
        customer.setId(customerId);

        BookingRequest request = new BookingRequest(
                staffId, LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                List.of(serviceId), null, null
        );

        when(shopAccessService.hasShopAccess(customerId, shopId)).thenReturn(false);
        when(serviceItemRepository.findAllById(List.of(serviceId))).thenReturn(List.of());

        assertThatThrownBy(() -> bookingService.createBooking(shopId, request, customer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("One or more services were not found");
    }

    @Test
    void createBooking_staffNotInShop_throwsException() {
        UUID serviceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        User customer = new User();
        customer.setId(customerId);

        BookingRequest request = new BookingRequest(
                staffId, LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                List.of(serviceId), null, null
        );

        serviceItem.setId(serviceId);

        when(shopAccessService.hasShopAccess(customerId, shopId)).thenReturn(false);
        when(serviceItemRepository.findAllById(List.of(serviceId))).thenReturn(List.of(serviceItem));
        when(shopAccessService.hasShopAccess(staffId, shopId)).thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(shopId, request, customer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("staff member does not belong to this shop");
    }

    @Test
    void listShopBookings_filtersCorrectly() {
        Booking pendingBooking = Booking.builder()
                .id(UUID.randomUUID()).shopId(shopId)
                .bookingDate(LocalDate.now()).status(BookingStatus.PENDING).build();

        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(bookingRepository.findByShopIdAndStatus(eq(shopId), eq(BookingStatus.PENDING), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(pendingBooking)));
        when(bookingMapper.toResponseList(List.of(pendingBooking)))
                .thenReturn(List.of(mock(BookingResponse.class)));

        BookingService.PagedBookingsResponse response =
                bookingService.listShopBookings(shopId, null, BookingStatus.PENDING, 0, 10, currentUserId);

        assertThat(response.bookings()).hasSize(1);
    }
}
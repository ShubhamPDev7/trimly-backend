package com.trimly.backend.service;

import com.trimly.backend.dto.servicerecord.ServiceRecordRequest;
import com.trimly.backend.dto.servicerecord.ServiceRecordResponse;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.ServiceRecord;
import com.trimly.backend.entity.User;
import com.trimly.backend.entity.WalkInQueueEntry;
import com.trimly.backend.enums.BookingStatus;
import com.trimly.backend.enums.WalkInStatus;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.exception.ShopAccessDeniedException;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.ServiceRecordRepository;
import com.trimly.backend.repository.WalkInQueueEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceRecordServiceTest {

    @Mock private ServiceRecordRepository serviceRecordRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private WalkInQueueEntryRepository walkInQueueEntryRepository;
    @Mock private ShopAccessService shopAccessService;

    @InjectMocks
    private ServiceRecordService serviceRecordService;

    private UUID shopId;
    private UUID bookingId;
    private UUID entryId;
    private UUID staffId;
    private UUID customerId;
    private User staffUser;
    private Booking completedBooking;
    private WalkInQueueEntry completedEntry;
    private ServiceRecordRequest request;

    @BeforeEach
    void setUp() {
        shopId     = UUID.randomUUID();
        bookingId  = UUID.randomUUID();
        entryId    = UUID.randomUUID();
        staffId    = UUID.randomUUID();
        customerId = UUID.randomUUID();

        staffUser = new User();
        staffUser.setId(staffId);

        completedBooking = Booking.builder()
                .id(bookingId)
                .shopId(shopId)
                .customerId(customerId)
                .status(BookingStatus.COMPLETED)
                .build();

        completedEntry = WalkInQueueEntry.builder()
                .id(entryId)
                .shopId(shopId)
                .customerId(customerId)
                .status(WalkInStatus.COMPLETED)
                .build();

        request = new ServiceRecordRequest(
                "Great haircut",
                List.of("Wahl Oil", "Layrite Pomade"),
                List.of("https://s3.example.com/photo1.jpg")
        );
    }

    @Test
    void createForBooking_succeeds() {
        doNothing().when(shopAccessService).verifyShopAccess(staffId, shopId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(completedBooking));
        when(serviceRecordRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
        when(serviceRecordRepository.save(any())).thenAnswer(inv -> {
            ServiceRecord r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(Instant.now());
            return r;
        });

        ServiceRecordResponse response = serviceRecordService.createForBooking(shopId, bookingId, request, staffUser);

        assertThat(response.shopId()).isEqualTo(shopId);
        assertThat(response.bookingId()).isEqualTo(bookingId);
        assertThat(response.staffId()).isEqualTo(staffId);
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.notes()).isEqualTo("Great haircut");
        assertThat(response.productsUsed()).containsExactly("Wahl Oil", "Layrite Pomade");
        assertThat(response.photoUrls()).hasSize(1);
        verify(serviceRecordRepository).save(any(ServiceRecord.class));
    }

    @Test
    void createForBooking_bookingNotFound_throws() {
        doNothing().when(shopAccessService).verifyShopAccess(staffId, shopId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                serviceRecordService.createForBooking(shopId, bookingId, request, staffUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Booking not found");
    }

    @Test
    void createForBooking_bookingBelongsToDifferentShop_throws() {
        completedBooking = Booking.builder()
                .id(bookingId)
                .shopId(UUID.randomUUID())
                .customerId(customerId)
                .status(BookingStatus.COMPLETED)
                .build();

        doNothing().when(shopAccessService).verifyShopAccess(staffId, shopId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(completedBooking));

        assertThatThrownBy(() ->
                serviceRecordService.createForBooking(shopId, bookingId, request, staffUser))
                .isInstanceOf(ShopAccessDeniedException.class);
    }

    @Test
    void createForBooking_bookingNotCompleted_throws() {
        completedBooking.setStatus(BookingStatus.PENDING);

        doNothing().when(shopAccessService).verifyShopAccess(staffId, shopId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(completedBooking));

        assertThatThrownBy(() ->
                serviceRecordService.createForBooking(shopId, bookingId, request, staffUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed booking");
    }

    @Test
    void createForBooking_duplicateRecord_throws() {
        doNothing().when(shopAccessService).verifyShopAccess(staffId, shopId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(completedBooking));
        when(serviceRecordRepository.findByBookingId(bookingId))
                .thenReturn(Optional.of(ServiceRecord.builder().build()));

        assertThatThrownBy(() ->
                serviceRecordService.createForBooking(shopId, bookingId, request, staffUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createForWalkIn_succeeds() {
        doNothing().when(shopAccessService).verifyShopAccess(staffId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(completedEntry));
        when(serviceRecordRepository.findByWalkInQueueEntryId(entryId)).thenReturn(Optional.empty());
        when(serviceRecordRepository.save(any())).thenAnswer(inv -> {
            ServiceRecord r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(Instant.now());
            return r;
        });

        ServiceRecordResponse response = serviceRecordService.createForWalkIn(shopId, entryId, request, staffUser);

        assertThat(response.walkInQueueEntryId()).isEqualTo(entryId);
        assertThat(response.bookingId()).isNull();
        verify(serviceRecordRepository).save(any(ServiceRecord.class));
    }

    @Test
    void createForWalkIn_entryNotCompleted_throws() {
        completedEntry.setStatus(WalkInStatus.WAITING);

        doNothing().when(shopAccessService).verifyShopAccess(staffId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(completedEntry));

        assertThatThrownBy(() ->
                serviceRecordService.createForWalkIn(shopId, entryId, request, staffUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed walk-in");
    }

    @Test
    void createForWalkIn_duplicateRecord_throws() {
        doNothing().when(shopAccessService).verifyShopAccess(staffId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(completedEntry));
        when(serviceRecordRepository.findByWalkInQueueEntryId(entryId))
                .thenReturn(Optional.of(ServiceRecord.builder().build()));

        assertThatThrownBy(() ->
                serviceRecordService.createForWalkIn(shopId, entryId, request, staffUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getShopRecords_returnsAllForShop() {
        ServiceRecord r1 = ServiceRecord.builder()
                .id(UUID.randomUUID()).shopId(shopId).staffId(staffId)
                .customerId(customerId).bookingId(bookingId)
                .createdAt(Instant.now()).build();
        ServiceRecord r2 = ServiceRecord.builder()
                .id(UUID.randomUUID()).shopId(shopId).staffId(staffId)
                .customerId(customerId).bookingId(UUID.randomUUID())
                .createdAt(Instant.now()).build();

        doNothing().when(shopAccessService).verifyShopAccess(staffId, shopId);
        when(serviceRecordRepository.findByShopIdOrderByCreatedAtDesc(shopId))
                .thenReturn(List.of(r1, r2));

        List<ServiceRecordResponse> result = serviceRecordService.getShopRecords(shopId, staffUser);

        assertThat(result).hasSize(2);
    }

    @Test
    void getMyStyleHistory_returnsCustomerRecords() {
        User customer = new User();
        customer.setId(customerId);

        ServiceRecord r = ServiceRecord.builder()
                .id(UUID.randomUUID()).shopId(shopId).staffId(staffId)
                .customerId(customerId).bookingId(bookingId)
                .createdAt(Instant.now()).build();

        when(serviceRecordRepository.findByCustomerIdOrderByCreatedAtDesc(customerId))
                .thenReturn(List.of(r));

        List<ServiceRecordResponse> result = serviceRecordService.getMyStyleHistory(customer);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).customerId()).isEqualTo(customerId);
    }
}
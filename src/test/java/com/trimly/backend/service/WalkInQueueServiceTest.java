package com.trimly.backend.service;

import com.trimly.backend.dto.bill.BillRequest;
import com.trimly.backend.dto.bill.BillResponse;
import com.trimly.backend.dto.walkin.WalkInJoinRequest;
import com.trimly.backend.dto.walkin.WalkInQueueEntryResponse;
import com.trimly.backend.dto.walkin.WalkInStartRequest;
import com.trimly.backend.entity.*;
import com.trimly.backend.enums.PaymentMode;
import com.trimly.backend.enums.PaymentStatus;
import com.trimly.backend.enums.ServiceCategory;
import com.trimly.backend.enums.WalkInStatus;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalkInQueueServiceTest {

    @Mock private WalkInQueueEntryRepository walkInQueueEntryRepository;
    @Mock private WalkInQueueServiceItemRepository walkInQueueServiceItemRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingServiceItemRepository bookingServiceItemRepository;
    @Mock private ServiceItemRepository serviceItemRepository;
    @Mock private ShopStaffRepository shopStaffRepository;
    @Mock private ShopAccessService shopAccessService;
    @Mock private ShopHoursService shopHoursService;
    @Mock private BillRepository billRepository;
    @Mock private LoyaltyService loyaltyService;
    @Mock private EmailService emailService;
    @Mock private UserRepository userRepository;
    @Mock private ShopRepository shopRepository;

    @InjectMocks
    private WalkInQueueService walkInQueueService;

    private UUID shopId;
    private UUID customerId;
    private UUID staffId;
    private UUID entryId;
    private UUID serviceId;

    private User customer;
    private User staffUser;
    private ServiceItem serviceItem;
    private WalkInQueueEntry waitingEntry;
    private WalkInQueueEntry inProgressEntry;
    private Shop shop;

    @BeforeEach
    void setUp() {
        shopId     = UUID.randomUUID();
        customerId = UUID.randomUUID();
        staffId    = UUID.randomUUID();
        entryId    = UUID.randomUUID();
        serviceId  = UUID.randomUUID();

        customer = new User();
        customer.setId(customerId);
        customer.setName("Vikram");
        customer.setEmail("vikram@example.com");

        staffUser = new User();
        staffUser.setId(staffId);
        staffUser.setName("Arjun");

        serviceItem = ServiceItem.builder()
                .id(serviceId)
                .shopId(shopId)
                .name("Haircut")
                .price(new BigDecimal("200.00"))
                .category(ServiceCategory.MALE)
                .estTimeMinutes(30)
                .build();

        waitingEntry = WalkInQueueEntry.builder()
                .id(entryId)
                .shopId(shopId)
                .customerId(customerId)
                .status(WalkInStatus.WAITING)
                .joinedAt(Instant.now())
                .build();

        inProgressEntry = WalkInQueueEntry.builder()
                .id(entryId)
                .shopId(shopId)
                .customerId(customerId)
                .assignedStaffId(staffId)
                .status(WalkInStatus.IN_PROGRESS)
                .joinedAt(Instant.now().minusSeconds(600))
                .startedAt(Instant.now().minusSeconds(300))
                .build();

        shop = new Shop();
        shop.setId(shopId);
        shop.setName("The Barber Shop");
        shop.setOwnerId(UUID.randomUUID());
        shop.setTimezone("Asia/Kolkata");
    }

    // ─── joinQueue ──────────────────────────────────────────────────────────────

    @Test
    void joinQueue_asCustomer_setsCustomerIdAndNoGuestInfo() {
        WalkInJoinRequest request = new WalkInJoinRequest(List.of(serviceId), null, null, null);

        when(shopAccessService.hasShopAccess(customerId, shopId)).thenReturn(false);
        when(serviceItemRepository.findAllById(List.of(serviceId))).thenReturn(List.of(serviceItem));
        when(walkInQueueEntryRepository.save(any())).thenReturn(waitingEntry);
        when(walkInQueueServiceItemRepository.saveAll(any())).thenReturn(List.of());
        when(walkInQueueEntryRepository.findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.WAITING))
                .thenReturn(List.of(waitingEntry));
        when(shopStaffRepository.findByShopId(shopId)).thenReturn(List.of());
        when(walkInQueueEntryRepository.findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.IN_PROGRESS))
                .thenReturn(List.of());
        when(bookingRepository.findByShopIdAndBookingDateBetween(eq(shopId), any(), any())).thenReturn(List.of());
        when(walkInQueueServiceItemRepository.findByQueueEntryId(any())).thenReturn(List.of());
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findById(shop.getOwnerId())).thenReturn(Optional.of(staffUser));

        WalkInQueueEntryResponse response = walkInQueueService.joinQueue(shopId, request, customer);

        assertThat(response).isNotNull();
        ArgumentCaptor<WalkInQueueEntry> captor = ArgumentCaptor.forClass(WalkInQueueEntry.class);
        verify(walkInQueueEntryRepository).save(captor.capture());
        WalkInQueueEntry saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getGuestName()).isNull();
        assertThat(saved.getGuestPhone()).isNull();
        assertThat(saved.getStatus()).isEqualTo(WalkInStatus.WAITING);
    }

    @Test
    void joinQueue_asStaff_withGuestInfo_succeeds() {
        WalkInJoinRequest request = new WalkInJoinRequest(List.of(serviceId), null, "Rahul", "9876543210");

        when(shopAccessService.hasShopAccess(staffId, shopId)).thenReturn(true);
        when(serviceItemRepository.findAllById(List.of(serviceId))).thenReturn(List.of(serviceItem));
        when(walkInQueueEntryRepository.save(any())).thenReturn(waitingEntry);
        when(walkInQueueServiceItemRepository.saveAll(any())).thenReturn(List.of());
        when(walkInQueueEntryRepository.findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.WAITING))
                .thenReturn(List.of(waitingEntry));
        when(shopStaffRepository.findByShopId(shopId)).thenReturn(List.of());
        when(walkInQueueEntryRepository.findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.IN_PROGRESS))
                .thenReturn(List.of());
        when(bookingRepository.findByShopIdAndBookingDateBetween(eq(shopId), any(), any())).thenReturn(List.of());
        when(walkInQueueServiceItemRepository.findByQueueEntryId(any())).thenReturn(List.of());
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(userRepository.findById(shop.getOwnerId())).thenReturn(Optional.of(staffUser));

        WalkInQueueEntryResponse response = walkInQueueService.joinQueue(shopId, request, staffUser);

        assertThat(response).isNotNull();
        ArgumentCaptor<WalkInQueueEntry> captor = ArgumentCaptor.forClass(WalkInQueueEntry.class);
        verify(walkInQueueEntryRepository).save(captor.capture());
        WalkInQueueEntry saved = captor.getValue();
        assertThat(saved.getCustomerId()).isNull();
        assertThat(saved.getGuestName()).isEqualTo("Rahul");
        assertThat(saved.getGuestPhone()).isEqualTo("9876543210");
    }

    @Test
    void joinQueue_asStaff_withoutGuestInfo_throws() {
        WalkInJoinRequest request = new WalkInJoinRequest(List.of(serviceId), null, null, null);

        when(shopAccessService.hasShopAccess(staffId, shopId)).thenReturn(true);

        assertThatThrownBy(() -> walkInQueueService.joinQueue(shopId, request, staffUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("guestName and guestPhone are required");
    }

    @Test
    void joinQueue_asStaff_withBlankGuestName_throws() {
        WalkInJoinRequest request = new WalkInJoinRequest(List.of(serviceId), null, "   ", "9876543210");

        when(shopAccessService.hasShopAccess(staffId, shopId)).thenReturn(true);

        assertThatThrownBy(() -> walkInQueueService.joinQueue(shopId, request, staffUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("guestName and guestPhone are required");
    }

    @Test
    void joinQueue_serviceNotFound_throws() {
        WalkInJoinRequest request = new WalkInJoinRequest(List.of(serviceId), null, null, null);

        when(shopAccessService.hasShopAccess(customerId, shopId)).thenReturn(false);
        when(serviceItemRepository.findAllById(List.of(serviceId))).thenReturn(List.of());

        assertThatThrownBy(() -> walkInQueueService.joinQueue(shopId, request, customer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("One or more services were not found");
    }

    @Test
    void joinQueue_serviceFromDifferentShop_throws() {
        ServiceItem foreignService = ServiceItem.builder()
                .id(serviceId)
                .shopId(UUID.randomUUID()) // different shop
                .name("Cut")
                .price(new BigDecimal("150.00"))
                .build();

        WalkInJoinRequest request = new WalkInJoinRequest(List.of(serviceId), null, null, null);

        when(shopAccessService.hasShopAccess(customerId, shopId)).thenReturn(false);
        when(serviceItemRepository.findAllById(List.of(serviceId))).thenReturn(List.of(foreignService));

        assertThatThrownBy(() -> walkInQueueService.joinQueue(shopId, request, customer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not belong to this shop");
    }

    @Test
    void joinQueue_preferredStaffNotInShop_throws() {
        UUID preferredStaffId = UUID.randomUUID();
        WalkInJoinRequest request = new WalkInJoinRequest(List.of(serviceId), preferredStaffId, null, null);

        when(shopAccessService.hasShopAccess(customerId, shopId)).thenReturn(false);
        when(serviceItemRepository.findAllById(List.of(serviceId))).thenReturn(List.of(serviceItem));
        when(shopAccessService.hasShopAccess(preferredStaffId, shopId)).thenReturn(false);

        assertThatThrownBy(() -> walkInQueueService.joinQueue(shopId, request, customer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("preferred staff member does not belong to this shop");
    }

    @Test
    void joinQueue_savesServiceItemsForEntry() {
        WalkInJoinRequest request = new WalkInJoinRequest(List.of(serviceId), null, null, null);

        when(shopAccessService.hasShopAccess(customerId, shopId)).thenReturn(false);
        when(serviceItemRepository.findAllById(List.of(serviceId))).thenReturn(List.of(serviceItem));
        when(walkInQueueEntryRepository.save(any())).thenReturn(waitingEntry);
        when(walkInQueueServiceItemRepository.saveAll(any())).thenReturn(List.of());
        when(walkInQueueEntryRepository.findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.WAITING))
                .thenReturn(List.of(waitingEntry));
        when(shopStaffRepository.findByShopId(shopId)).thenReturn(List.of());
        when(walkInQueueEntryRepository.findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.IN_PROGRESS))
                .thenReturn(List.of());
        when(bookingRepository.findByShopIdAndBookingDateBetween(eq(shopId), any(), any())).thenReturn(List.of());
        when(walkInQueueServiceItemRepository.findByQueueEntryId(any())).thenReturn(List.of());
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findById(shop.getOwnerId())).thenReturn(Optional.of(staffUser));

        walkInQueueService.joinQueue(shopId, request, customer);

        ArgumentCaptor<List<WalkInQueueServiceItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(walkInQueueServiceItemRepository).saveAll(captor.capture());
        List<WalkInQueueServiceItem> savedItems = captor.getValue();
        assertThat(savedItems).hasSize(1);
        assertThat(savedItems.get(0).getServiceId()).isEqualTo(serviceId);
        assertThat(savedItems.get(0).getPriceAtJoin()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    // ─── listQueue ───────────────────────────────────────────────────────────────

    @Test
    void listQueue_returnsWaitingAndInProgressEntries() {
        WalkInQueueServiceItem serviceItemLink = WalkInQueueServiceItem.builder()
                .id(UUID.randomUUID())
                .queueEntryId(entryId)
                .serviceId(serviceId)
                .priceAtJoin(new BigDecimal("200.00"))
                .build();

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findByShopIdAndStatusInOrderByJoinedAtAsc(eq(shopId), any()))
                .thenReturn(List.of(waitingEntry, inProgressEntry));
        when(shopStaffRepository.findByShopId(shopId)).thenReturn(List.of());
        when(walkInQueueEntryRepository.findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.WAITING))
                .thenReturn(List.of(waitingEntry));
        when(walkInQueueEntryRepository.findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.IN_PROGRESS))
                .thenReturn(List.of(inProgressEntry));
        when(bookingRepository.findByShopIdAndBookingDateBetween(eq(shopId), any(), any())).thenReturn(List.of());
        when(walkInQueueServiceItemRepository.findByQueueEntryId(any())).thenReturn(List.of(serviceItemLink));
        when(serviceItemRepository.findAllById(any())).thenReturn(List.of(serviceItem));

        List<WalkInQueueEntryResponse> responses = walkInQueueService.listQueue(shopId, customerId);

        assertThat(responses).hasSize(2);
        // Waiting entry gets a position; in-progress does not
        assertThat(responses.get(0).queuePosition()).isEqualTo(1);
        assertThat(responses.get(1).queuePosition()).isNull();
    }

    @Test
    void listQueue_emptyQueue_returnsEmptyList() {
        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findByShopIdAndStatusInOrderByJoinedAtAsc(eq(shopId), any()))
                .thenReturn(List.of());
        when(shopStaffRepository.findByShopId(shopId)).thenReturn(List.of());
        when(walkInQueueEntryRepository.findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.WAITING))
                .thenReturn(List.of());
        when(walkInQueueEntryRepository.findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.IN_PROGRESS))
                .thenReturn(List.of());
        when(bookingRepository.findByShopIdAndBookingDateBetween(eq(shopId), any(), any())).thenReturn(List.of());

        List<WalkInQueueEntryResponse> responses = walkInQueueService.listQueue(shopId, customerId);

        assertThat(responses).isEmpty();
    }

    // ─── getQueuePosition ───────────────────────────────────────────────────────

    @Test
    void getQueuePosition_waitingEntry_returnsPositionAndEstimate() {
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(waitingEntry));
        when(walkInQueueEntryRepository.findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.WAITING))
                .thenReturn(List.of(waitingEntry));
        when(shopStaffRepository.findByShopId(shopId)).thenReturn(List.of());
        when(walkInQueueEntryRepository.findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.IN_PROGRESS))
                .thenReturn(List.of());
        when(bookingRepository.findByShopIdAndBookingDateBetween(eq(shopId), any(), any())).thenReturn(List.of());
        when(walkInQueueServiceItemRepository.findByQueueEntryId(any())).thenReturn(List.of());

        WalkInQueueService.QueuePositionSnapshot snapshot =
                walkInQueueService.getQueuePosition(shopId, entryId);

        assertThat(snapshot.entryId()).isEqualTo(entryId);
        assertThat(snapshot.status()).isEqualTo(WalkInStatus.WAITING);
        assertThat(snapshot.position()).isEqualTo(1);
    }

    @Test
    void getQueuePosition_inProgressEntry_returnsNullPosition() {
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(inProgressEntry));

        WalkInQueueService.QueuePositionSnapshot snapshot =
                walkInQueueService.getQueuePosition(shopId, entryId);

        assertThat(snapshot.status()).isEqualTo(WalkInStatus.IN_PROGRESS);
        assertThat(snapshot.position()).isNull();
        assertThat(snapshot.estimatedWaitMinutes()).isNull();
    }

    @Test
    void getQueuePosition_completedEntry_returnsNullPosition() {
        WalkInQueueEntry completedEntry = WalkInQueueEntry.builder()
                .id(entryId).shopId(shopId).status(WalkInStatus.COMPLETED).build();

        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(completedEntry));

        WalkInQueueService.QueuePositionSnapshot snapshot =
                walkInQueueService.getQueuePosition(shopId, entryId);

        assertThat(snapshot.status()).isEqualTo(WalkInStatus.COMPLETED);
        assertThat(snapshot.position()).isNull();
    }

    @Test
    void getQueuePosition_entryNotFound_throws() {
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walkInQueueService.getQueuePosition(shopId, entryId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Queue entry not found");
    }

    @Test
    void getQueuePosition_entryBelongsToDifferentShop_throws() {
        WalkInQueueEntry foreignEntry = WalkInQueueEntry.builder()
                .id(entryId).shopId(UUID.randomUUID()).status(WalkInStatus.WAITING).build();

        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(foreignEntry));

        assertThatThrownBy(() -> walkInQueueService.getQueuePosition(shopId, entryId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── startQueueEntry ────────────────────────────────────────────────────────

    @Test
    void startQueueEntry_waitingEntry_setsInProgressAndAssignsStaff() {
        WalkInStartRequest request = new WalkInStartRequest(staffId);

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(shopAccessService.hasShopAccess(staffId, shopId)).thenReturn(true);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(waitingEntry));
        when(walkInQueueEntryRepository.save(any())).thenReturn(waitingEntry);
        when(walkInQueueServiceItemRepository.findByQueueEntryId(any())).thenReturn(List.of());
        when(serviceItemRepository.findAllById(any())).thenReturn(List.of());

        WalkInQueueEntryResponse response = walkInQueueService.startQueueEntry(shopId, entryId, request, customerId);

        assertThat(response).isNotNull();
        assertThat(waitingEntry.getStatus()).isEqualTo(WalkInStatus.IN_PROGRESS);
        assertThat(waitingEntry.getAssignedStaffId()).isEqualTo(staffId);
        assertThat(waitingEntry.getStartedAt()).isNotNull();
    }

    @Test
    void startQueueEntry_staffNotInShop_throws() {
        WalkInStartRequest request = new WalkInStartRequest(staffId);

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(shopAccessService.hasShopAccess(staffId, shopId)).thenReturn(false);

        assertThatThrownBy(() -> walkInQueueService.startQueueEntry(shopId, entryId, request, customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to this shop");
    }

    @Test
    void startQueueEntry_alreadyInProgress_throws() {
        WalkInStartRequest request = new WalkInStartRequest(staffId);

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(shopAccessService.hasShopAccess(staffId, shopId)).thenReturn(true);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(inProgressEntry));

        assertThatThrownBy(() -> walkInQueueService.startQueueEntry(shopId, entryId, request, customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only a waiting queue entry can be started");
    }

    @Test
    void startQueueEntry_entryNotFound_throws() {
        WalkInStartRequest request = new WalkInStartRequest(staffId);

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(shopAccessService.hasShopAccess(staffId, shopId)).thenReturn(true);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walkInQueueService.startQueueEntry(shopId, entryId, request, customerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── completeQueueEntry ─────────────────────────────────────────────────────

    @Test
    void completeQueueEntry_inProgressEntry_setsCompleted() {
        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(inProgressEntry));
        when(walkInQueueEntryRepository.save(any())).thenReturn(inProgressEntry);
        when(walkInQueueServiceItemRepository.findByQueueEntryId(any())).thenReturn(List.of());
        when(serviceItemRepository.findAllById(any())).thenReturn(List.of());

        WalkInQueueEntryResponse response = walkInQueueService.completeQueueEntry(shopId, entryId, customerId);

        assertThat(response).isNotNull();
        assertThat(inProgressEntry.getStatus()).isEqualTo(WalkInStatus.COMPLETED);
        assertThat(inProgressEntry.getCompletedAt()).isNotNull();
    }

    @Test
    void completeQueueEntry_waitingEntry_throws() {
        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(waitingEntry));

        assertThatThrownBy(() -> walkInQueueService.completeQueueEntry(shopId, entryId, customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only an in-progress queue entry can be completed");
    }

    @Test
    void completeQueueEntry_alreadyCompleted_throws() {
        WalkInQueueEntry completedEntry = WalkInQueueEntry.builder()
                .id(entryId).shopId(shopId).status(WalkInStatus.COMPLETED).build();

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(completedEntry));

        assertThatThrownBy(() -> walkInQueueService.completeQueueEntry(shopId, entryId, customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only an in-progress queue entry can be completed");
    }

    // ─── cancelQueueEntry ───────────────────────────────────────────────────────

    @Test
    void cancelQueueEntry_waitingEntry_setsCancelled() {
        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(waitingEntry));
        when(walkInQueueEntryRepository.save(any())).thenReturn(waitingEntry);
        when(walkInQueueServiceItemRepository.findByQueueEntryId(any())).thenReturn(List.of());
        when(serviceItemRepository.findAllById(any())).thenReturn(List.of());

        WalkInQueueEntryResponse response = walkInQueueService.cancelQueueEntry(shopId, entryId, customerId);

        assertThat(response).isNotNull();
        assertThat(waitingEntry.getStatus()).isEqualTo(WalkInStatus.CANCELLED);
    }

    @Test
    void cancelQueueEntry_inProgressEntry_setsCancelled() {
        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(inProgressEntry));
        when(walkInQueueEntryRepository.save(any())).thenReturn(inProgressEntry);
        when(walkInQueueServiceItemRepository.findByQueueEntryId(any())).thenReturn(List.of());
        when(serviceItemRepository.findAllById(any())).thenReturn(List.of());

        WalkInQueueEntryResponse response = walkInQueueService.cancelQueueEntry(shopId, entryId, customerId);

        assertThat(inProgressEntry.getStatus()).isEqualTo(WalkInStatus.CANCELLED);
        assertThat(response).isNotNull();
    }

    @Test
    void cancelQueueEntry_completedEntry_throws() {
        WalkInQueueEntry completedEntry = WalkInQueueEntry.builder()
                .id(entryId).shopId(shopId).status(WalkInStatus.COMPLETED).build();

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(completedEntry));

        assertThatThrownBy(() -> walkInQueueService.cancelQueueEntry(shopId, entryId, customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only a waiting or in-progress queue entry can be cancelled");
    }

    @Test
    void cancelQueueEntry_noShowEntry_throws() {
        WalkInQueueEntry noShowEntry = WalkInQueueEntry.builder()
                .id(entryId).shopId(shopId).status(WalkInStatus.NO_SHOW).build();

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(noShowEntry));

        assertThatThrownBy(() -> walkInQueueService.cancelQueueEntry(shopId, entryId, customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only a waiting or in-progress queue entry can be cancelled");
    }

    // ─── markNoShow ─────────────────────────────────────────────────────────────

    @Test
    void markNoShow_waitingEntry_setsNoShow() {
        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(waitingEntry));
        when(walkInQueueEntryRepository.save(any())).thenReturn(waitingEntry);
        when(walkInQueueServiceItemRepository.findByQueueEntryId(any())).thenReturn(List.of());
        when(serviceItemRepository.findAllById(any())).thenReturn(List.of());

        WalkInQueueEntryResponse response = walkInQueueService.markNoShow(shopId, entryId, customerId);

        assertThat(waitingEntry.getStatus()).isEqualTo(WalkInStatus.NO_SHOW);
        assertThat(response).isNotNull();
    }

    @Test
    void markNoShow_inProgressEntry_throws() {
        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(inProgressEntry));

        assertThatThrownBy(() -> walkInQueueService.markNoShow(shopId, entryId, customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only a waiting queue entry can be marked as a no-show");
    }

    @Test
    void markNoShow_completedEntry_throws() {
        WalkInQueueEntry completedEntry = WalkInQueueEntry.builder()
                .id(entryId).shopId(shopId).status(WalkInStatus.COMPLETED).build();

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(completedEntry));

        assertThatThrownBy(() -> walkInQueueService.markNoShow(shopId, entryId, customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only a waiting queue entry can be marked as a no-show");
    }

    // ─── createWalkInBill ───────────────────────────────────────────────────────

    @Test
    void createWalkInBill_completedEntry_createsBillAndAwardsLoyaltyPoints() {
        WalkInQueueEntry completedEntry = WalkInQueueEntry.builder()
                .id(entryId).shopId(shopId).customerId(customerId)
                .status(WalkInStatus.COMPLETED).build();

        WalkInQueueServiceItem queueServiceItem = WalkInQueueServiceItem.builder()
                .id(UUID.randomUUID()).queueEntryId(entryId)
                .serviceId(serviceId).priceAtJoin(new BigDecimal("200.00"))
                .build();

        Bill savedBill = Bill.builder()
                .id(UUID.randomUUID()).shopId(shopId)
                .walkInQueueEntryId(entryId)
                .totalAmount(new BigDecimal("200.00"))
                .paymentMode(PaymentMode.CASH)
                .paymentStatus(PaymentStatus.PAID)
                .createdAt(Instant.now())
                .build();

        BillRequest request = new BillRequest(PaymentMode.CASH);

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(completedEntry));
        when(billRepository.findByWalkInQueueEntryId(entryId)).thenReturn(Optional.empty());
        when(walkInQueueServiceItemRepository.findByQueueEntryId(entryId)).thenReturn(List.of(queueServiceItem));
        when(billRepository.save(any())).thenReturn(savedBill);

        BillResponse response = walkInQueueService.createWalkInBill(shopId, entryId, request, customerId);

        assertThat(response).isNotNull();
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        verify(loyaltyService).awardPoints(eq(shopId), eq(customerId), any(), eq(new BigDecimal("200.00")));
    }

    @Test
    void createWalkInBill_multipleServices_totalsSummedCorrectly() {
        UUID serviceId2 = UUID.randomUUID();
        WalkInQueueEntry completedEntry = WalkInQueueEntry.builder()
                .id(entryId).shopId(shopId).customerId(customerId)
                .status(WalkInStatus.COMPLETED).build();

        WalkInQueueServiceItem item1 = WalkInQueueServiceItem.builder()
                .id(UUID.randomUUID()).queueEntryId(entryId)
                .serviceId(serviceId).priceAtJoin(new BigDecimal("200.00")).build();
        WalkInQueueServiceItem item2 = WalkInQueueServiceItem.builder()
                .id(UUID.randomUUID()).queueEntryId(entryId)
                .serviceId(serviceId2).priceAtJoin(new BigDecimal("150.00")).build();

        Bill savedBill = Bill.builder()
                .id(UUID.randomUUID()).shopId(shopId)
                .walkInQueueEntryId(entryId)
                .totalAmount(new BigDecimal("350.00"))
                .paymentMode(PaymentMode.CASH)
                .paymentStatus(PaymentStatus.PAID)
                .createdAt(Instant.now())
                .build();

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(completedEntry));
        when(billRepository.findByWalkInQueueEntryId(entryId)).thenReturn(Optional.empty());
        when(walkInQueueServiceItemRepository.findByQueueEntryId(entryId)).thenReturn(List.of(item1, item2));
        when(billRepository.save(any())).thenReturn(savedBill);

        BillResponse response = walkInQueueService.createWalkInBill(shopId, entryId, new BillRequest(PaymentMode.CASH), customerId);

        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo(new BigDecimal("350.00"));
    }

    @Test
    void createWalkInBill_notCompleted_throws() {
        BillRequest request = new BillRequest(PaymentMode.CASH);

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(waitingEntry));

        assertThatThrownBy(() -> walkInQueueService.createWalkInBill(shopId, entryId, request, customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only completed walk-in entries can be billed");
    }

    @Test
    void createWalkInBill_inProgressEntry_throws() {
        BillRequest request = new BillRequest(PaymentMode.CASH);

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(inProgressEntry));

        assertThatThrownBy(() -> walkInQueueService.createWalkInBill(shopId, entryId, request, customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only completed walk-in entries can be billed");
    }

    @Test
    void createWalkInBill_alreadyBilled_throws() {
        WalkInQueueEntry completedEntry = WalkInQueueEntry.builder()
                .id(entryId).shopId(shopId).status(WalkInStatus.COMPLETED).build();

        BillRequest request = new BillRequest(PaymentMode.CASH);

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(completedEntry));
        when(billRepository.findByWalkInQueueEntryId(entryId))
                .thenReturn(Optional.of(mock(Bill.class)));

        assertThatThrownBy(() -> walkInQueueService.createWalkInBill(shopId, entryId, request, customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been billed");
    }

    @Test
    void createWalkInBill_guestWalkIn_doesNotAwardLoyaltyPoints() {
        WalkInQueueEntry completedGuestEntry = WalkInQueueEntry.builder()
                .id(entryId).shopId(shopId)
                .customerId(null) // guest — no customer ID
                .guestName("Rahul").guestPhone("9876543210")
                .status(WalkInStatus.COMPLETED).build();

        WalkInQueueServiceItem queueServiceItem = WalkInQueueServiceItem.builder()
                .id(UUID.randomUUID()).queueEntryId(entryId)
                .serviceId(serviceId).priceAtJoin(new BigDecimal("200.00")).build();

        Bill savedBill = Bill.builder()
                .id(UUID.randomUUID()).shopId(shopId)
                .walkInQueueEntryId(entryId)
                .totalAmount(new BigDecimal("200.00"))
                .paymentMode(PaymentMode.CASH)
                .paymentStatus(PaymentStatus.PAID)
                .createdAt(Instant.now())
                .build();

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(completedGuestEntry));
        when(billRepository.findByWalkInQueueEntryId(entryId)).thenReturn(Optional.empty());
        when(walkInQueueServiceItemRepository.findByQueueEntryId(entryId)).thenReturn(List.of(queueServiceItem));
        when(billRepository.save(any())).thenReturn(savedBill);

        walkInQueueService.createWalkInBill(shopId, entryId, new BillRequest(PaymentMode.CASH), customerId);

        // awardPoints should be called with null customerId — LoyaltyService handles the null guard
        verify(loyaltyService).awardPoints(eq(shopId), isNull(), any(), any());
    }

    @Test
    void createWalkInBill_entryFromDifferentShop_throws() {
        WalkInQueueEntry foreignEntry = WalkInQueueEntry.builder()
                .id(entryId).shopId(UUID.randomUUID()).status(WalkInStatus.COMPLETED).build();

        doNothing().when(shopAccessService).verifyShopAccess(customerId, shopId);
        when(walkInQueueEntryRepository.findById(entryId)).thenReturn(Optional.of(foreignEntry));

        assertThatThrownBy(() -> walkInQueueService.createWalkInBill(shopId, entryId, new BillRequest(PaymentMode.CASH), customerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
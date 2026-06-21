package com.trimly.backend.service;

import com.trimly.backend.dto.dashboard.DashboardSummaryResponse;
import com.trimly.backend.dto.dashboard.StaffPerformanceResponse;
import com.trimly.backend.entity.Bill;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.ShopStaff;
import com.trimly.backend.entity.User;
import com.trimly.backend.enums.BookingStatus;
import com.trimly.backend.repository.BillRepository;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.ShopStaffRepository;
import com.trimly.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private BillRepository billRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private ShopAccessService shopAccessService;
    @Mock private ShopStaffRepository shopStaffRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private UUID shopId;
    private UUID currentUserId;
    private UUID staffId;
    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeEach
    void setUp() {
        shopId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        startDate = LocalDate.of(2025, 1, 1);
        endDate = LocalDate.of(2025, 1, 31);
    }

    @Test
    void getSummary_noBookingsNoBills_returnsZeroRevenue() {
        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(billRepository.findByShopIdAndCreatedAtBetween(eq(shopId), any(), any())).thenReturn(List.of());
        when(bookingRepository.findByShopIdAndBookingDateBetween(shopId, startDate, endDate)).thenReturn(List.of());

        DashboardSummaryResponse response = dashboardService.getSummary(shopId, startDate, endDate, currentUserId);

        assertThat(response.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalBookings()).isEqualTo(0);
        assertThat(response.dailyBreakdown()).isEmpty();
        assertThat(response.topCustomers()).isEmpty();
    }

    @Test
    void getSummary_withBills_calculatesTotalRevenueCorrectly() {
        UUID bookingId1 = UUID.randomUUID();
        UUID bookingId2 = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Booking booking1 = Booking.builder()
                .id(bookingId1)
                .shopId(shopId)
                .customerId(customerId)
                .bookingDate(LocalDate.of(2025, 1, 10))
                .status(BookingStatus.COMPLETED)
                .build();

        Booking booking2 = Booking.builder()
                .id(bookingId2)
                .shopId(shopId)
                .customerId(customerId)
                .bookingDate(LocalDate.of(2025, 1, 15))
                .status(BookingStatus.COMPLETED)
                .build();

        Bill bill1 = Bill.builder()
                .id(UUID.randomUUID())
                .shopId(shopId)
                .bookingId(bookingId1)
                .totalAmount(new BigDecimal("500.00"))
                .createdAt(Instant.parse("2025-01-10T10:00:00Z"))
                .build();

        Bill bill2 = Bill.builder()
                .id(UUID.randomUUID())
                .shopId(shopId)
                .bookingId(bookingId2)
                .totalAmount(new BigDecimal("300.00"))
                .createdAt(Instant.parse("2025-01-15T10:00:00Z"))
                .build();

        User customer = new User();
        customer.setId(customerId);
        customer.setName("Rahul");

        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(billRepository.findByShopIdAndCreatedAtBetween(eq(shopId), any(), any())).thenReturn(List.of(bill1, bill2));
        when(bookingRepository.findByShopIdAndBookingDateBetween(shopId, startDate, endDate)).thenReturn(List.of(booking1, booking2));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));

        DashboardSummaryResponse response = dashboardService.getSummary(shopId, startDate, endDate, currentUserId);

        assertThat(response.totalRevenue()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(response.totalBookings()).isEqualTo(2);
        assertThat(response.dailyBreakdown()).hasSize(2);
    }

    @Test
    void getStaffPerformance_returnsStaffSortedByRevenue() {
        UUID staffId2 = UUID.randomUUID();
        UUID bookingId1 = UUID.randomUUID();
        UUID bookingId2 = UUID.randomUUID();

        Booking booking1 = Booking.builder()
                .id(bookingId1).shopId(shopId).staffId(staffId)
                .bookingDate(LocalDate.of(2025, 1, 10))
                .status(BookingStatus.COMPLETED).build();

        Booking booking2 = Booking.builder()
                .id(bookingId2).shopId(shopId).staffId(staffId2)
                .bookingDate(LocalDate.of(2025, 1, 12))
                .status(BookingStatus.COMPLETED).build();

        Bill bill1 = Bill.builder()
                .bookingId(bookingId1).totalAmount(new BigDecimal("700.00"))
                .createdAt(Instant.parse("2025-01-10T10:00:00Z")).build();

        Bill bill2 = Bill.builder()
                .bookingId(bookingId2).totalAmount(new BigDecimal("300.00"))
                .createdAt(Instant.parse("2025-01-12T10:00:00Z")).build();

        ShopStaff staffLink1 = new ShopStaff();
        staffLink1.setUserId(staffId);

        ShopStaff staffLink2 = new ShopStaff();
        staffLink2.setUserId(staffId2);

        User user1 = new User();
        user1.setId(staffId);
        user1.setName("Amit");

        User user2 = new User();
        user2.setId(staffId2);
        user2.setName("Ravi");

        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(bookingRepository.findByShopIdAndBookingDateBetween(shopId, startDate, endDate)).thenReturn(List.of(booking1, booking2));
        when(billRepository.findByShopIdAndCreatedAtBetween(eq(shopId), any(), any())).thenReturn(List.of(bill1, bill2));
        when(shopStaffRepository.findByShopId(shopId)).thenReturn(List.of(staffLink1, staffLink2));
        when(userRepository.findAllById(any())).thenReturn(List.of(user1, user2));

        List<StaffPerformanceResponse> response = dashboardService.getStaffPerformance(shopId, startDate, endDate, currentUserId);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).staffName()).isEqualTo("Amit");
        assertThat(response.get(0).totalRevenue()).isEqualByComparingTo(new BigDecimal("700.00"));
        assertThat(response.get(1).staffName()).isEqualTo("Ravi");
        assertThat(response.get(1).totalRevenue()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void getStaffPerformance_noCompletedBookings_returnsZeroRevenueForAllStaff() {
        ShopStaff staffLink = new ShopStaff();
        staffLink.setUserId(staffId);

        User user = new User();
        user.setId(staffId);
        user.setName("Amit");

        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(bookingRepository.findByShopIdAndBookingDateBetween(shopId, startDate, endDate)).thenReturn(List.of());
        when(billRepository.findByShopIdAndCreatedAtBetween(eq(shopId), any(), any())).thenReturn(List.of());
        when(shopStaffRepository.findByShopId(shopId)).thenReturn(List.of(staffLink));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        List<StaffPerformanceResponse> response = dashboardService.getStaffPerformance(shopId, startDate, endDate, currentUserId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.get(0).bookingsCompleted()).isEqualTo(0);
    }
}
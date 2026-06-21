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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final BillRepository billRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ShopAccessService shopAccessService;
    private final ShopStaffRepository shopStaffRepository;

    public DashboardSummaryResponse getSummary(UUID shopId, LocalDate startDate, LocalDate endDate, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        Instant start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Bill> bills = billRepository.findByShopIdAndCreatedAtBetween(shopId, start, end);

        BigDecimal totalRevenue = bills.stream()
                .map(Bill::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<LocalDate, BigDecimal> revenueByDate = bills.stream()
                .collect(Collectors.groupingBy(
                        bill -> bill.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.reducing(BigDecimal.ZERO, Bill::getTotalAmount, BigDecimal::add)
                ));

        List<DashboardSummaryResponse.DailyRevenue> dailyBreakdown = revenueByDate.entrySet().stream()
                .map(e -> new DashboardSummaryResponse.DailyRevenue(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(DashboardSummaryResponse.DailyRevenue::date))
                .collect(Collectors.toList());

        List<Booking> bookingsInRange = bookingRepository.findByShopIdAndBookingDateBetween(shopId, startDate, endDate);

        List<DashboardSummaryResponse.TopCustomer> topCustomers = buildTopCustomers(bills, bookingsInRange);

        return new DashboardSummaryResponse(totalRevenue, bookingsInRange.size(), dailyBreakdown, topCustomers);
    }

    public List<StaffPerformanceResponse> getStaffPerformance(UUID shopId, LocalDate startDate, LocalDate endDate, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        Instant start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Booking> completedBookings = bookingRepository
                .findByShopIdAndBookingDateBetween(shopId, startDate, endDate).stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .collect(Collectors.toList());

        List<Bill> bills = billRepository.findByShopIdAndCreatedAtBetween(shopId, start, end);
        Map<UUID, BigDecimal> revenueByBookingId = bills.stream()
                .collect(Collectors.toMap(Bill::getBookingId, Bill::getTotalAmount, (a, b) -> a));

        Map<UUID, List<Booking>> bookingsByStaffId = completedBookings.stream()
                .collect(Collectors.groupingBy(Booking::getStaffId));

        List<ShopStaff> staffLinks = shopStaffRepository.findByShopId(shopId);
        Map<UUID, User> usersById = userRepository.findAllById(
                staffLinks.stream().map(ShopStaff::getUserId).collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(User::getId, u -> u));

        return staffLinks.stream()
                .map(link -> {
                    UUID staffId = link.getUserId();
                    List<Booking> staffBookings = bookingsByStaffId.getOrDefault(staffId, List.of());

                    BigDecimal staffRevenue = staffBookings.stream()
                            .map(b -> revenueByBookingId.getOrDefault(b.getId(), BigDecimal.ZERO))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    User user = usersById.get(staffId);

                    return new StaffPerformanceResponse(
                            staffId,
                            user != null ? user.getName() : "Unknown",
                            staffBookings.size(),
                            staffRevenue
                    );
                })
                .sorted(Comparator.comparing(StaffPerformanceResponse::totalRevenue).reversed())
                .collect(Collectors.toList());
    }

    private List<DashboardSummaryResponse.TopCustomer> buildTopCustomers(List<Bill> bills, List<Booking> bookingsInRange) {
        Map<UUID, Booking> bookingsById = bookingsInRange.stream()
                .collect(Collectors.toMap(Booking::getId, b -> b, (a, b) -> a));

        Map<String, List<Bill>> billsByCustomerKey = bills.stream()
                .filter(bill -> bookingsById.containsKey(bill.getBookingId()))
                .collect(Collectors.groupingBy(bill -> resolveCustomerKey(bookingsById.get(bill.getBookingId()))));

        return billsByCustomerKey.entrySet().stream()
                .map(entry -> {
                    List<Bill> customerBills = entry.getValue();
                    BigDecimal totalSpent = customerBills.stream()
                            .map(Bill::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    Booking sampleBooking = bookingsById.get(customerBills.get(0).getBookingId());
                    String label = resolveCustomerLabel(sampleBooking);

                    return new DashboardSummaryResponse.TopCustomer(label, customerBills.size(), totalSpent);
                })
                .sorted(Comparator.comparing(DashboardSummaryResponse.TopCustomer::totalSpent).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    private String resolveCustomerKey(Booking booking) {
        if (booking.getCustomerId() != null) {
            return "customer:" + booking.getCustomerId();
        }
        String guestPhone = booking.getGuestPhone();
        return guestPhone != null ? "guest:" + guestPhone : "guest-unknown:" + booking.getId();
    }

    private String resolveCustomerLabel(Booking booking) {
        if (booking.getCustomerId() != null) {
            return userRepository.findById(booking.getCustomerId())
                    .map(User::getName)
                    .orElse("Unknown Customer");
        }
        return booking.getGuestName() != null ? booking.getGuestName() : "Guest";
    }
}
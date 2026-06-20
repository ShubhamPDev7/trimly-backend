package com.trimly.backend.controller;

import com.trimly.backend.dto.dashboard.DashboardSummaryResponse;
import com.trimly.backend.entity.Bill;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.User;
import com.trimly.backend.repository.BillRepository;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.UserRepository;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.ShopAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shops/{shopId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final BillRepository billRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ShopAccessService shopAccessService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary(
            @PathVariable UUID shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal CustomUserDetails userDetails
            ) {
        shopAccessService.verifyShopAccess(userDetails.getUser().getId(), shopId);

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

        List<Booking> bookingsInRange = bookingRepository.findByShopId(shopId).stream()
                .filter(b -> !b.getBookingDate().isBefore(startDate) && !b.getBookingDate().isAfter(endDate))
                .collect(Collectors.toList());

        List<DashboardSummaryResponse.TopCustomer> topCustomers = buildTopCustomers(bills, bookingsInRange);

        DashboardSummaryResponse response = new DashboardSummaryResponse(
                totalRevenue,
                bookingsInRange.size(),
                dailyBreakdown,
                topCustomers
        );

        return ResponseEntity.ok(response);
    }

    private List<DashboardSummaryResponse.TopCustomer> buildTopCustomers(List<Bill> bills, List<Booking> bookingsInRange) {
        Map<UUID, Booking> bookingsById = bookingsInRange.stream()
                .collect(Collectors.toMap(Booking::getId, b -> b, (a, b) -> a));

        Map<String, List<Bill>> billsByCustomerLabel = bills.stream()
                .filter(bill -> bookingsById.containsKey(bill.getBookingId()))
                .collect(Collectors.groupingBy(bill -> resolveCustomerLabel(bookingsById.get(bill.getBookingId()))));

        return billsByCustomerLabel.entrySet().stream()
                .map(entry -> {
                    List<Bill> customerBills = entry.getValue();
                    BigDecimal totalSpent = customerBills.stream()
                            .map(Bill::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new DashboardSummaryResponse.TopCustomer(entry.getKey(), customerBills.size(), totalSpent);
                })
                .sorted(Comparator.comparing(DashboardSummaryResponse.TopCustomer::totalSpent).reversed())
                .limit(10)
                .collect(Collectors.toList());
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

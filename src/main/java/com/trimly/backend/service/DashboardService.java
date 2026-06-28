package com.trimly.backend.service;

import com.trimly.backend.dto.dashboard.DashboardSummaryResponse;
import com.trimly.backend.dto.dashboard.PeakHoursResponse;
import com.trimly.backend.dto.dashboard.ShopOverviewResponse;
import com.trimly.backend.dto.dashboard.StaffPerformanceResponse;
import com.trimly.backend.dto.dashboard.TopServicesResponse;
import com.trimly.backend.entity.Bill;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.ShopStaff;
import com.trimly.backend.entity.User;
import com.trimly.backend.enums.BookingStatus;
import com.trimly.backend.enums.PaymentStatus;
import com.trimly.backend.repository.BillRepository;
import com.trimly.backend.repository.BookingServiceItemRepository;
import com.trimly.backend.repository.ServiceItemRepository;
import com.trimly.backend.repository.ReviewRepository;
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
    private final ReviewRepository reviewRepository;
    private final BookingServiceItemRepository bookingServiceItemRepository;
    private final ServiceItemRepository serviceItemRepository;

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

                    Double avgRating = reviewRepository.findAverageRatingByStaffId(staffId, shopId);
                    long reviewCount = reviewRepository.countByStaffId(staffId, shopId);
                    Double roundedRating = avgRating != null
                            ? Math.round(avgRating * 10.0) / 10.0
                            : null;

                    return new StaffPerformanceResponse(
                            staffId,
                            user != null ? user.getName() : "Unknown",
                            staffBookings.size(),
                            staffRevenue,
                            roundedRating,
                            reviewCount
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

    // ── Peak Hours ────────────────────────────────────────────────────────────

    public PeakHoursResponse getPeakHours(UUID shopId, LocalDate startDate, LocalDate endDate, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        List<Booking> bookings = bookingRepository.findByShopIdAndBookingDateBetween(shopId, startDate, endDate)
                .stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED && b.getStatus() != BookingStatus.REJECTED)
                .collect(Collectors.toList());

        // Count bookings per hour (0–23)
        Map<Integer, Long> countByHour = bookings.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getTimeSlot().getHour(),
                        Collectors.counting()
                ));

        // Build all 24 slots, defaulting to 0
        List<PeakHoursResponse.HourSlot> slots = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            String label = formatHour(hour);
            slots.add(new PeakHoursResponse.HourSlot(hour, label, countByHour.getOrDefault(hour, 0L)));
        }

        return new PeakHoursResponse(slots);
    }

    private String formatHour(int hour) {
        if (hour == 0) return "12:00 AM";
        if (hour < 12) return hour + ":00 AM";
        if (hour == 12) return "12:00 PM";
        return (hour - 12) + ":00 PM";
    }

    // ── Top Services ─────────────────────────────────────────────────────────

    public TopServicesResponse getTopServices(UUID shopId, LocalDate startDate, LocalDate endDate, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        List<Booking> bookings = bookingRepository.findByShopIdAndBookingDateBetween(shopId, startDate, endDate)
                .stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .collect(Collectors.toList());

        if (bookings.isEmpty()) {
            return new TopServicesResponse(List.of());
        }

        List<UUID> bookingIds = bookings.stream().map(Booking::getId).collect(Collectors.toList());
        List<Object[]> rows = bookingServiceItemRepository.findServiceStatsByBookingIds(bookingIds);

        // Load service names in one query
        List<UUID> serviceIds = rows.stream()
                .map(r -> (UUID) r[0])
                .collect(Collectors.toList());

        Map<UUID, String> serviceNames = serviceItemRepository.findAllById(serviceIds)
                .stream()
                .collect(Collectors.toMap(s -> s.getId(), s -> s.getName()));

        List<TopServicesResponse.ServiceStat> stats = rows.stream()
                .map(r -> {
                    UUID serviceId = (UUID) r[0];
                    long bookingCount = ((Number) r[1]).longValue();
                    BigDecimal totalRevenue = (BigDecimal) r[2];
                    String name = serviceNames.getOrDefault(serviceId, "Unknown Service");
                    return new TopServicesResponse.ServiceStat(serviceId, name, bookingCount, totalRevenue);
                })
                .limit(10)
                .collect(Collectors.toList());

        return new TopServicesResponse(stats);
    }

    // ── Shop Overview ─────────────────────────────────────────────────────────

    public ShopOverviewResponse getOverview(UUID shopId, LocalDate startDate, LocalDate endDate, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        Instant start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Booking> allBookings = bookingRepository.findByShopIdAndBookingDateBetween(shopId, startDate, endDate);
        List<Bill> bills = billRepository.findByShopIdAndCreatedAtBetween(shopId, start, end)
                .stream()
                .filter(b -> b.getPaymentStatus() == PaymentStatus.PAID)
                .collect(Collectors.toList());

        long total = allBookings.size();
        long completed = allBookings.stream().filter(b -> b.getStatus() == BookingStatus.COMPLETED).count();
        long cancelled = allBookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();

        double cancellationRate = total == 0 ? 0 : Math.round((cancelled * 100.0 / total) * 10.0) / 10.0;

        // Unique customers (registered only — guests counted by phone)
        Set<UUID> customerIds = allBookings.stream()
                .filter(b -> b.getCustomerId() != null)
                .map(Booking::getCustomerId)
                .collect(Collectors.toSet());

        Set<String> guestPhones = allBookings.stream()
                .filter(b -> b.getCustomerId() == null && b.getGuestPhone() != null)
                .map(Booking::getGuestPhone)
                .collect(Collectors.toSet());

        long totalUniqueCustomers = customerIds.size() + guestPhones.size();

        // Repeat customers = those with more than 1 booking in the period
        long repeatCustomers = allBookings.stream()
                .filter(b -> b.getCustomerId() != null)
                .collect(Collectors.groupingBy(Booking::getCustomerId, Collectors.counting()))
                .values().stream()
                .filter(count -> count > 1)
                .count();

        double repeatCustomerRate = customerIds.isEmpty() ? 0
                : Math.round((repeatCustomers * 100.0 / customerIds.size()) * 10.0) / 10.0;

        // Average bill value
        BigDecimal avgBillValue = bills.isEmpty() ? BigDecimal.ZERO
                : bills.stream()
                .map(Bill::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(bills.size()), 2, java.math.RoundingMode.HALF_UP);

        Double avgRating = reviewRepository.findAverageRatingByShopId(shopId);
        Double roundedRating = avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : null;
        long totalReviews = reviewRepository.countByShopId(shopId);

        return new ShopOverviewResponse(
                total,
                completed,
                cancelled,
                cancellationRate,
                totalUniqueCustomers,
                repeatCustomers,
                repeatCustomerRate,
                avgBillValue,
                roundedRating,
                totalReviews
        );
    }

}
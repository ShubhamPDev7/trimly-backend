package com.trimly.backend.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardSummaryResponse(
        BigDecimal totalRevenue,
        long totalBookings,
        List<DailyRevenue> dailyBreakdown,
        List<TopCustomer> topCustomers
) {

    public record DailyRevenue(
            LocalDate date,
            BigDecimal revenue
    ) {
    }

    public record TopCustomer(
            String label,
            long visitCount,
            BigDecimal totalSpent
    ) {

    }

}

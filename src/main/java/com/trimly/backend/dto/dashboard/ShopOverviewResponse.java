package com.trimly.backend.dto.dashboard;

import java.math.BigDecimal;

public record ShopOverviewResponse(
        long totalBookings,
        long completedBookings,
        long cancelledBookings,
        double cancellationRate,        // percentage e.g. 12.5
        long totalUniqueCustomers,
        long repeatCustomers,
        double repeatCustomerRate,      // percentage
        BigDecimal averageBillValue,
        Double averageShopRating,
        long totalReviews
) {}
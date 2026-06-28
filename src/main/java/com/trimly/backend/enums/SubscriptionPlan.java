package com.trimly.backend.enums;

import lombok.Getter;

@Getter
public enum SubscriptionPlan {

    FREE(
            2,
            100,
            false,
            false,
            false
    ),
    PRO(
            10,
            1000,
            true,
            true,
            false
    ),
    ENTERPRISE(
            -1,
            -1,
            true,
            true,
            true
    );

    private final int maxStaff;
    private final int maxBookingsPerMonth;
    private final boolean dashboardEnabled;
    private final boolean analyticsEnabled;
    private final boolean multibranchEnabled;

    SubscriptionPlan(int maxStaff, int maxBookingsPerMonth,
                     boolean dashboardEnabled, boolean analyticsEnabled,
                     boolean multibranchEnabled) {
        this.maxStaff = maxStaff;
        this.maxBookingsPerMonth = maxBookingsPerMonth;
        this.dashboardEnabled = dashboardEnabled;
        this.analyticsEnabled = analyticsEnabled;
        this.multibranchEnabled = multibranchEnabled;
    }

    public boolean isUnlimited() {
        return this == ENTERPRISE;
    }
}
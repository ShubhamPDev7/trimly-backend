package com.trimly.backend.dto.booking;

import com.trimly.backend.dto.policy.CancellationPolicyResponse;
import com.trimly.backend.dto.service.ServiceItemResponse;
import com.trimly.backend.dto.shop.BarberPublicProfile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record BookingSummaryResponse(
        UUID shopId,
        String shopName,
        ServiceItemResponse service,
        BarberPublicProfile staff,
        LocalDate date,
        List<LocalTime> availableSlots,
        CancellationPolicyResponse cancellationPolicy
) {}
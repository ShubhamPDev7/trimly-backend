package com.trimly.backend.dto.shop;

import com.trimly.backend.dto.hours.ShopHoursResponse;
import com.trimly.backend.dto.service.ServiceItemResponse;

import java.util.List;
import java.util.UUID;

public record ShopPublicProfileResponse(
        UUID id,
        String name,
        String address,
        String locality,
        String timezone,
        Double averageRating,
        Integer totalReviews,
        List<ServiceItemResponse> services,
        List<ShopHoursResponse> hours,
        List<BarberPublicProfile> staff
) {}
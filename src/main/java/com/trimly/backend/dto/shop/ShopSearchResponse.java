package com.trimly.backend.dto.shop;

import java.util.UUID;

public record ShopSearchResponse(
        UUID id,
        String name,
        String address,
        String locality,
        String timezone,
        Double averageRating,
        Integer totalReviews,
        Integer totalServices,
        boolean openNow
) {}
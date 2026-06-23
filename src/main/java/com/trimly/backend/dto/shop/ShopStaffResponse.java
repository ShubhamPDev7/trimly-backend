package com.trimly.backend.dto.shop;

import com.trimly.backend.enums.StaffRole;

import java.util.UUID;

public record ShopStaffResponse(
        UUID userId,
        String name,
        String email,
        StaffRole roleInShop
) {
}
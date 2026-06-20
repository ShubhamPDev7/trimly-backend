package com.trimly.backend.dto.shop;

import java.util.UUID;

public record ShopStaffResponse(
        UUID userId,
        String name,
        String email,
        String roleInShop
) {
}

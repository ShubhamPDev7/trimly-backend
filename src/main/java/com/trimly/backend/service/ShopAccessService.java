package com.trimly.backend.service;

import com.trimly.backend.exception.ShopAccessDeniedException;
import com.trimly.backend.repository.ShopStaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopAccessService {

    private final ShopStaffRepository shopStaffRepository;

    public void verifyShopAccess(UUID userId, UUID shopId) {
        boolean hasAccess = shopStaffRepository.findByShopIdAndUserId(shopId, userId).isPresent();

        if (!hasAccess) {
            throw new ShopAccessDeniedException("You do not have access to this shop");
        }
    }

}

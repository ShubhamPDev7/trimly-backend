package com.trimly.backend.service;

import com.trimly.backend.entity.Shop;
import com.trimly.backend.entity.ShopStaff;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.enums.StaffRole;
import com.trimly.backend.exception.ShopAccessDeniedException;
import com.trimly.backend.repository.ShopRepository;
import com.trimly.backend.repository.ShopStaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopAccessService {

    private final ShopStaffRepository shopStaffRepository;
    private final ShopRepository shopRepository;

    public boolean hasShopAccess(UUID userId, UUID shopId) {
        return shopStaffRepository.findByShopIdAndUserId(shopId, userId).isPresent();
    }

    public void verifyShopAccess(UUID userId, UUID shopId) {
        verifyShopExists(shopId);

        if (!hasShopAccess(userId, shopId)) {
            throw new ShopAccessDeniedException("You do not have access to this shop");
        }
    }

    public void verifyShopOwner(UUID userId, UUID shopId) {
        verifyShopExists(shopId);

        ShopStaff staffLink = shopStaffRepository.findByShopIdAndUserId(shopId, userId)
                .orElseThrow(() -> new ShopAccessDeniedException("You do not have access to this shop"));

        if (staffLink.getRoleInShop() != StaffRole.OWNER) {
            throw new ShopAccessDeniedException("Only the shop owner can perform this action");
        }
    }

    private void verifyShopExists(UUID shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found."));

        if (shop.isDeleted()) {
            throw new ResourceNotFoundException("Shop not found.");
        }
    }

}
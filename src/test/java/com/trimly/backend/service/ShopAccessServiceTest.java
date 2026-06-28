package com.trimly.backend.service;

import com.trimly.backend.entity.Shop;
import com.trimly.backend.entity.ShopStaff;
import com.trimly.backend.enums.StaffRole;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.exception.ShopAccessDeniedException;
import com.trimly.backend.repository.ShopRepository;
import com.trimly.backend.repository.ShopStaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopAccessServiceTest {

    @Mock private ShopStaffRepository shopStaffRepository;
    @Mock private ShopRepository shopRepository;

    @InjectMocks
    private ShopAccessService shopAccessService;

    private UUID shopId;
    private UUID userId;
    private Shop shop;

    @BeforeEach
    void setUp() {
        shopId = UUID.randomUUID();
        userId = UUID.randomUUID();

        shop = Shop.builder()
                .id(shopId)
                .name("Trimly")
                .ownerId(userId)
                .deleted(false)
                .build();
    }

    @Test
    void hasShopAccess_whenStaffExists_returnsTrue() {
        when(shopStaffRepository.findByShopIdAndUserId(shopId, userId)).thenReturn(Optional.of(new ShopStaff()));

        assertThat(shopAccessService.hasShopAccess(userId, shopId)).isTrue();
    }

    @Test
    void hasShopAccess_whenNoStaff_returnsFalse() {
        when(shopStaffRepository.findByShopIdAndUserId(shopId, userId)).thenReturn(Optional.empty());

        assertThat(shopAccessService.hasShopAccess(userId, shopId)).isFalse();
    }

    @Test
    void verifyShopAccess_shopNotFound_throwsException() {
        when(shopRepository.findById(shopId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopAccessService.verifyShopAccess(userId, shopId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Shop not found");
    }

    @Test
    void verifyShopAccess_shopDeleted_throwsException() {
        shop.setDeleted(true);
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));

        assertThatThrownBy(() -> shopAccessService.verifyShopAccess(userId, shopId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Shop not found");
    }

    @Test
    void verifyShopAccess_noAccess_throwsException() {
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(shopStaffRepository.findByShopIdAndUserId(shopId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopAccessService.verifyShopAccess(userId, shopId))
                .isInstanceOf(ShopAccessDeniedException.class)
                .hasMessageContaining("do not have access");
    }

    @Test
    void verifyShopOwner_notOwner_throwsException() {
        ShopStaff staffLink = new ShopStaff();
        staffLink.setRoleInShop(StaffRole.STAFF);

        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(shopStaffRepository.findByShopIdAndUserId(shopId, userId)).thenReturn(Optional.of(staffLink));

        assertThatThrownBy(() -> shopAccessService.verifyShopOwner(userId, shopId))
                .isInstanceOf(ShopAccessDeniedException.class)
                .hasMessageContaining("Only the shop owner");
    }

    @Test
    void verifyShopOwner_isOwner_doesNotThrow() {
        ShopStaff staffLink = new ShopStaff();
        staffLink.setRoleInShop(StaffRole.OWNER);

        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(shopStaffRepository.findByShopIdAndUserId(shopId, userId)).thenReturn(Optional.of(staffLink));

        shopAccessService.verifyShopOwner(userId, shopId);

        assertThat(true).isTrue();
    }
}
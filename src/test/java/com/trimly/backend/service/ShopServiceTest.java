package com.trimly.backend.service;

import com.trimly.backend.dto.shop.AddStaffRequest;
import com.trimly.backend.dto.shop.ShopStaffResponse;
import com.trimly.backend.entity.Shop;
import com.trimly.backend.entity.ShopStaff;
import com.trimly.backend.entity.User;
import com.trimly.backend.enums.Role;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.ShopRepository;
import com.trimly.backend.repository.ShopStaffRepository;
import com.trimly.backend.repository.UserRepository;
import com.trimly.backend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock private ShopRepository shopRepository;
    @Mock private ShopStaffRepository shopStaffRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private ShopAccessService shopAccessService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ShopService shopService;

    private UUID shopId;
    private UUID ownerId;
    private User owner;
    private Shop shop;

    @BeforeEach
    void setUp() {
        shopId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        owner = new User();
        owner.setId(ownerId);
        owner.setName("Shubham");
        owner.setEmail("shubham@test.com");
        owner.setRole(Role.OWNER);

        shop = Shop.builder()
                .id(shopId)
                .name("Trimly Barber")
                .address("Pune")
                .ownerId(ownerId)
                .build();
    }

    @Test
    void addStaff_userNotFound_throwsException() {
        AddStaffRequest request = new AddStaffRequest("notfound@test.com", "Barber");

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(userRepository.findByEmail("notfound@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.addStaff(shopId, request, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No account found with this email");
    }

    @Test
    void addStaff_alreadyStaff_throwsException() {
        User staffUser = new User();
        staffUser.setId(UUID.randomUUID());
        staffUser.setEmail("staff@test.com");

        AddStaffRequest request = new AddStaffRequest("staff@test.com", "Barber");

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(userRepository.findByEmail("staff@test.com")).thenReturn(Optional.of(staffUser));
        when(shopStaffRepository.findByShopIdAndUserId(shopId, staffUser.getId())).thenReturn(Optional.of(new ShopStaff()));

        assertThatThrownBy(() -> shopService.addStaff(shopId, request, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already staff");
    }

    @Test
    void addStaff_success_returnsStaffResponse() {
        User staffUser = new User();
        staffUser.setId(UUID.randomUUID());
        staffUser.setName("Ravi");
        staffUser.setEmail("ravi@test.com");

        AddStaffRequest request = new AddStaffRequest("ravi@test.com", "Barber");

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(userRepository.findByEmail("ravi@test.com")).thenReturn(Optional.of(staffUser));
        when(shopStaffRepository.findByShopIdAndUserId(shopId, staffUser.getId())).thenReturn(Optional.empty());
        when(shopStaffRepository.save(any())).thenReturn(new ShopStaff());

        ShopStaffResponse response = shopService.addStaff(shopId, request, ownerId);

        assertThat(response.name()).isEqualTo("Ravi");
        assertThat(response.roleInShop()).isEqualTo("Barber");
    }

    @Test
    void deleteShop_softDeletesAndSaves() {
        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(shopRepository.save(shop)).thenReturn(shop);

        shopService.deleteShop(shopId, ownerId);

        assertThat(shop.isDeleted()).isTrue();
        assertThat(shop.getDeletedAt()).isNotNull();
        verify(shopRepository).save(shop);
    }

    @Test
    void deleteShop_alreadyDeleted_throwsException() {
        shop.setDeleted(true);

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));

        assertThatThrownBy(() -> shopService.deleteShop(shopId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Shop not found");
    }

    @Test
    void listStaff_returnsAllStaffWithNames() {
        UUID staffUserId = UUID.randomUUID();

        ShopStaff staffLink = new ShopStaff();
        staffLink.setUserId(staffUserId);
        staffLink.setRoleInShop("Barber");

        User staffUser = new User();
        staffUser.setId(staffUserId);
        staffUser.setName("Amit");
        staffUser.setEmail("amit@test.com");

        doNothing().when(shopAccessService).verifyShopAccess(ownerId, shopId);
        when(shopStaffRepository.findByShopId(shopId)).thenReturn(List.of(staffLink));
        when(userRepository.findAllById(any())).thenReturn(List.of(staffUser));

        List<ShopStaffResponse> response = shopService.listStaff(shopId, ownerId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).name()).isEqualTo("Amit");
        assertThat(response.get(0).roleInShop()).isEqualTo("Barber");
    }

    @Test
    void createShop_savesShopAndOwnerLink_returnsResponseWithToken() {
        com.trimly.backend.dto.shop.ShopRequest request = new com.trimly.backend.dto.shop.ShopRequest("Trimly Barber", "Pune", "Kothrud");

        Shop savedShop = Shop.builder()
                .id(shopId)
                .name("Trimly Barber")
                .address("Pune")
                .locality("Kothrud")
                .ownerId(ownerId)
                .build();

        ShopStaff ownerLink = ShopStaff.builder()
                .shopId(shopId)
                .userId(ownerId)
                .roleInShop("Owner")
                .build();

        when(shopRepository.save(any())).thenReturn(savedShop);
        when(shopStaffRepository.save(any())).thenReturn(ownerLink);
        when(shopStaffRepository.findByUserId(ownerId)).thenReturn(List.of(ownerLink));
        when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("mocked-jwt-token");

        com.trimly.backend.dto.shop.ShopResponse response = shopService.createShop(request, owner);

        assertThat(response.name()).isEqualTo("Trimly Barber");
        assertThat(response.ownerId()).isEqualTo(ownerId);
        assertThat(response.token()).isEqualTo("mocked-jwt-token");
        verify(shopRepository).save(any());
        verify(shopStaffRepository).save(any());
    }
}
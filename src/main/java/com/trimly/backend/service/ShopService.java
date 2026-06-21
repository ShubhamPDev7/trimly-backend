package com.trimly.backend.service;

import com.trimly.backend.dto.shop.AddStaffRequest;
import com.trimly.backend.dto.shop.ShopRequest;
import com.trimly.backend.dto.shop.ShopResponse;
import com.trimly.backend.dto.shop.ShopStaffResponse;
import com.trimly.backend.entity.Shop;
import com.trimly.backend.entity.ShopStaff;
import com.trimly.backend.entity.User;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.ShopRepository;
import com.trimly.backend.repository.ShopStaffRepository;
import com.trimly.backend.repository.UserRepository;
import com.trimly.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;
    private final ShopStaffRepository shopStaffRepository;
    private final JwtUtil jwtUtil;
    private final ShopAccessService shopAccessService;
    private final UserRepository userRepository;

    @Transactional
    public ShopResponse createShop(ShopRequest request, User currentUser) {
        Shop shop = Shop.builder()
                .name(request.name())
                .address(request.address())
                .locality(request.locality())
                .ownerId(currentUser.getId())
                .build();

        shop = shopRepository.save(shop);

        ShopStaff ownerLink = ShopStaff.builder()
                .shopId(shop.getId())
                .userId(currentUser.getId())
                .roleInShop("Owner")
                .build();

        shopStaffRepository.save(ownerLink);

        List<UUID> shopIds = shopStaffRepository.findByUserId(currentUser.getId()).stream()
                .map(ShopStaff::getShopId)
                .collect(Collectors.toList());

        String newToken = jwtUtil.generateToken(
                currentUser.getId(),
                currentUser.getEmail(),
                currentUser.getRole().name(),
                shopIds
        );

        return new ShopResponse(
                shop.getId(),
                shop.getName(),
                shop.getAddress(),
                shop.getLocality(),
                shop.getOwnerId(),
                shop.getCreatedAt(),
                newToken
        );
    }

    public List<ShopStaffResponse> listStaff(UUID shopId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);
        List<ShopStaff> staffLinks = shopStaffRepository.findByShopId(shopId);

        List<UUID> userIds = staffLinks.stream().map(ShopStaff::getUserId).collect(Collectors.toList());
        Map<UUID, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return staffLinks.stream()
                .map(link -> {
                    User user = usersById.get(link.getUserId());
                    return new ShopStaffResponse(
                            link.getUserId(),
                            user != null ? user.getName() : "Unknown",
                            user != null ? user.getEmail() : "Unknown",
                            link.getRoleInShop()
                    );
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public ShopStaffResponse addStaff(UUID shopId, AddStaffRequest request, UUID currentUserId) {
        shopAccessService.verifyShopOwner(currentUserId, shopId);

        User userToAdd = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No account found with this email. The person must register on Trimly before being added as staff."));

        if (shopStaffRepository.findByShopIdAndUserId(shopId, userToAdd.getId()).isPresent()) {
            throw new IllegalArgumentException("This person is already staff at this shop.");
        }

        ShopStaff staffLink = ShopStaff.builder()
                .shopId(shopId)
                .userId(userToAdd.getId())
                .roleInShop(request.roleInShop())
                .build();

        shopStaffRepository.save(staffLink);

        return new ShopStaffResponse(userToAdd.getId(), userToAdd.getName(), userToAdd.getEmail(), request.roleInShop());
    }

    @Transactional
    public void deleteShop(UUID shopId, UUID currentUserId) {
        shopAccessService.verifyShopOwner(currentUserId, shopId);

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found."));

        if (shop.isDeleted()) {
            throw new ResourceNotFoundException("Shop not found.");
        }

        shop.setDeleted(true);
        shop.setDeletedAt(Instant.now());
        shopRepository.save(shop);
    }

}
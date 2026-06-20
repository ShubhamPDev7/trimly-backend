package com.trimly.backend.controller;

import com.trimly.backend.dto.shop.AddStaffRequest;
import com.trimly.backend.dto.shop.ShopRequest;
import com.trimly.backend.dto.shop.ShopResponse;
import com.trimly.backend.dto.shop.ShopStaffResponse;
import com.trimly.backend.entity.Shop;
import com.trimly.backend.entity.ShopStaff;
import com.trimly.backend.entity.User;
import com.trimly.backend.repository.ShopRepository;
import com.trimly.backend.repository.ShopStaffRepository;
import com.trimly.backend.repository.UserRepository;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.security.JwtUtil;
import com.trimly.backend.service.ShopAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
public class ShopController {

    private final ShopRepository shopRepository;
    private final ShopStaffRepository shopStaffRepository;
    private final JwtUtil jwtUtil;
    private final ShopAccessService shopAccessService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ShopResponse> createShop(
            @Valid @RequestBody ShopRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
            ) {
        User currentUser = userDetails.getUser();

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

        ShopResponse response = new ShopResponse(
                shop.getId(),
                shop.getName(),
                shop.getAddress(),
                shop.getLocality(),
                shop.getOwnerId(),
                shop.getCreatedAt(),
                newToken
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shopId}/staff")
    public ResponseEntity<List<ShopStaffResponse>> listStaff(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        shopAccessService.verifyShopAccess(userDetails.getUser().getId(), shopId);
        List<ShopStaff> staffLinks = shopStaffRepository.findByShopId(shopId);

        List<UUID> userIds = staffLinks.stream().map(ShopStaff::getUserId).collect(Collectors.toList());
        Map<UUID, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ShopStaffResponse> responses = staffLinks.stream()
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
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{shopId}/staff")
    public ResponseEntity<ShopStaffResponse> addStaff(
            @PathVariable UUID shopId,
            @Valid @RequestBody AddStaffRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        shopAccessService.verifyShopOwner(userDetails.getUser().getId(), shopId);

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

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ShopStaffResponse(userToAdd.getId(), userToAdd.getName(), userToAdd.getEmail(), request.roleInShop())
        );
    }

}

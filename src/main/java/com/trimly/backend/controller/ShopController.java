package com.trimly.backend.controller;

import com.trimly.backend.dto.shop.ShopRequest;
import com.trimly.backend.dto.shop.ShopResponse;
import com.trimly.backend.entity.Shop;
import com.trimly.backend.entity.ShopStaff;
import com.trimly.backend.entity.User;
import com.trimly.backend.repository.ShopRepository;
import com.trimly.backend.repository.ShopStaffRepository;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
public class ShopController {

    private final ShopRepository shopRepository;
    private final ShopStaffRepository shopStaffRepository;
    private final JwtUtil jwtUtil;

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

}

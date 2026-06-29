package com.trimly.backend.controller;

import com.trimly.backend.dto.shop.ShopPublicProfileResponse;
import com.trimly.backend.dto.shop.ShopSearchResponse;
import com.trimly.backend.service.ShopSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ShopSearchController {

    private final ShopSearchService shopSearchService;

    @GetMapping("/api/v1/shops/search")
    public ResponseEntity<List<ShopSearchResponse>> searchShops(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String locality
    ) {
        return ResponseEntity.ok(shopSearchService.searchShops(query, locality));
    }

    @GetMapping("/api/v1/shops/localities")
    public ResponseEntity<List<String>> getLocalities() {
        return ResponseEntity.ok(shopSearchService.getLocalities());
    }

    @GetMapping("/api/v1/shops/{shopId}/public")
    public ResponseEntity<ShopPublicProfileResponse> getPublicProfile(
            @PathVariable UUID shopId
    ) {
        return ResponseEntity.ok(shopSearchService.getPublicProfile(shopId));
    }
}
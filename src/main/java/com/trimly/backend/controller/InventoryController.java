package com.trimly.backend.controller;

import com.trimly.backend.dto.inventory.InventoryRequest;
import com.trimly.backend.dto.inventory.InventoryResponse;
import com.trimly.backend.dto.inventory.InventoryUsageRequest;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping
    public ResponseEntity<InventoryResponse> createItem(
            @PathVariable UUID shopId,
            @Valid @RequestBody InventoryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.createItem(shopId, request, userDetails.getUser().getId()));
    }

    @GetMapping
    public ResponseEntity<List<InventoryResponse>> listItems(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(inventoryService.listItems(shopId, userDetails.getUser().getId()));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<InventoryResponse>> listLowStock(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(inventoryService.listLowStock(shopId, userDetails.getUser().getId()));
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<InventoryResponse> updateItem(
            @PathVariable UUID shopId,
            @PathVariable UUID itemId,
            @Valid @RequestBody InventoryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                inventoryService.updateItem(shopId, itemId, request, userDetails.getUser().getId()));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable UUID shopId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        inventoryService.deleteItem(shopId, itemId, userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/service-records/{serviceRecordId}/usage")
    public ResponseEntity<Void> recordUsage(
            @PathVariable UUID shopId,
            @PathVariable UUID serviceRecordId,
            @Valid @RequestBody List<InventoryUsageRequest> usages,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        inventoryService.recordUsage(shopId, serviceRecordId, usages, userDetails.getUser().getId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
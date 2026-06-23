package com.trimly.backend.controller;

import com.trimly.backend.dto.service.ServiceItemRequest;
import com.trimly.backend.dto.service.ServiceItemResponse;
import com.trimly.backend.enums.ServiceCategory;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.ServiceItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/services")
@RequiredArgsConstructor
public class ServiceItemController {

    private final ServiceItemService serviceItemService;

    @PostMapping
    public ResponseEntity<ServiceItemResponse> createService(
            @PathVariable UUID shopId,
            @Valid @RequestBody ServiceItemRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ServiceItemResponse response = serviceItemService.createService(shopId, request, userDetails.getUser().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ServiceItemResponse>> listServices(
            @PathVariable UUID shopId,
            @RequestParam(required = false) ServiceCategory category
    ) {
        List<ServiceItemResponse> response = serviceItemService.listServices(shopId, category);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{serviceId}")
    public ResponseEntity<ServiceItemResponse> updateService(
            @PathVariable UUID shopId,
            @PathVariable UUID serviceId,
            @Valid @RequestBody ServiceItemRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ServiceItemResponse response = serviceItemService.updateService(shopId, serviceId, request, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> deleteService(
            @PathVariable UUID shopId,
            @PathVariable UUID serviceId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        serviceItemService.deleteService(shopId, serviceId, userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }

}
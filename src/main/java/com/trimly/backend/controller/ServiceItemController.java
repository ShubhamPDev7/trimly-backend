package com.trimly.backend.controller;

import com.trimly.backend.dto.service.ServiceItemRequest;
import com.trimly.backend.dto.service.ServiceItemResponse;
import com.trimly.backend.entity.ServiceItem;
import com.trimly.backend.enums.ServiceCategory;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.ServiceItemRepository;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.ShopAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shops/{shopId}/services")
@RequiredArgsConstructor
public class ServiceItemController {

    private final ServiceItemRepository serviceItemRepository;
    private final ShopAccessService shopAccessService;

    @PostMapping
    public ResponseEntity<ServiceItemResponse> createService(
            @PathVariable UUID shopId,
            @Valid @RequestBody ServiceItemRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        shopAccessService.verifyShopAccess(userDetails.getUser().getId(), shopId);

        ServiceItem service = ServiceItem.builder()
                .shopId(shopId)
                .category(request.category())
                .name(request.name())
                .price(request.price())
                .estTimeMinutes(request.estTimeMinutes())
                .imageUrl(request.imageUrl())
                .build();

        service = serviceItemRepository.save(service);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(service));
    }

    @GetMapping
    public ResponseEntity<List<ServiceItemResponse>> listServices(
            @PathVariable UUID shopId,
            @RequestParam(required = false) ServiceCategory category
    ) {
        List<ServiceItem> services = (category != null)
                ? serviceItemRepository.findByShopIdAndCategory(shopId, category)
                : serviceItemRepository.findByShopId(shopId);

        List<ServiceItemResponse> response = services.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{serviceId}")
    public ResponseEntity<ServiceItemResponse> updateService(
            @PathVariable UUID shopId,
            @PathVariable UUID serviceId,
            @Valid @RequestBody ServiceItemRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        shopAccessService.verifyShopAccess(userDetails.getUser().getId(), shopId);

        ServiceItem service = serviceItemRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found."));

        if (!service.getShopId().equals(shopId)) {
            throw new ResourceNotFoundException("Service not found.");
        }

        service.setCategory(request.category());
        service.setName(request.name());
        service.setPrice(request.price());
        service.setEstTimeMinutes(request.estTimeMinutes());
        service.setImageUrl(request.imageUrl());

        service = serviceItemRepository.save(service);

        return ResponseEntity.ok(toResponse(service));
    }

    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> deleteService(
            @PathVariable UUID shopId,
            @PathVariable UUID serviceId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        shopAccessService.verifyShopAccess(userDetails.getUser().getId(), shopId);

        ServiceItem service = serviceItemRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found."));

        if (!service.getShopId().equals(shopId)) {
            throw new ResourceNotFoundException("Service not found.");
        }

        serviceItemRepository.delete(service);

        return ResponseEntity.noContent().build();
    }

    private ServiceItemResponse toResponse(ServiceItem service) {
        return new ServiceItemResponse(
                service.getId(),
                service.getShopId(),
                service.getCategory(),
                service.getName(),
                service.getPrice(),
                service.getEstTimeMinutes(),
                service.getImageUrl()
        );
    }
}
package com.trimly.backend.service;

import com.trimly.backend.dto.service.ServiceItemRequest;
import com.trimly.backend.dto.service.ServiceItemResponse;
import com.trimly.backend.entity.ServiceItem;
import com.trimly.backend.enums.ServiceCategory;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.ServiceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceItemService {

    private final ServiceItemRepository serviceItemRepository;
    private final ShopAccessService shopAccessService;

    @Transactional
    public ServiceItemResponse createService(UUID shopId, ServiceItemRequest request, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        ServiceItem service = ServiceItem.builder()
                .shopId(shopId)
                .category(request.category())
                .name(request.name())
                .price(request.price())
                .estTimeMinutes(request.estTimeMinutes())
                .imageUrl(request.imageUrl())
                .build();

        service = serviceItemRepository.save(service);

        return toResponse(service);
    }

    public List<ServiceItemResponse> listServices(UUID shopId, ServiceCategory category) {
        List<ServiceItem> services = (category != null)
                ? serviceItemRepository.findByShopIdAndCategoryAndDeletedFalse(shopId, category)
                : serviceItemRepository.findByShopIdAndDeletedFalse(shopId);

        return services.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ServiceItemResponse updateService(UUID shopId, UUID serviceId, ServiceItemRequest request, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        ServiceItem service = serviceItemRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found."));

        if (!service.getShopId().equals(shopId)) {
            throw new ResourceNotFoundException("Service not found.");
        }

        if (service.isDeleted()) {
            throw new ResourceNotFoundException("Service not found.");
        }

        service.setCategory(request.category());
        service.setName(request.name());
        service.setPrice(request.price());
        service.setEstTimeMinutes(request.estTimeMinutes());
        service.setImageUrl(request.imageUrl());

        service = serviceItemRepository.save(service);

        return toResponse(service);
    }

    @Transactional
    public void deleteService(UUID shopId, UUID serviceId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        ServiceItem service = serviceItemRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found."));

        if (!service.getShopId().equals(shopId)) {
            throw new ResourceNotFoundException("Service not found.");
        }

        if (service.isDeleted()) {
            throw new ResourceNotFoundException("Service not found.");
        }

        service.setDeleted(true);
        service.setDeletedAt(Instant.now());
        serviceItemRepository.save(service);
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
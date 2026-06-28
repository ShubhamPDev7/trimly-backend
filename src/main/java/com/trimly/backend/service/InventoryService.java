package com.trimly.backend.service;

import com.trimly.backend.dto.inventory.InventoryRequest;
import com.trimly.backend.dto.inventory.InventoryResponse;
import com.trimly.backend.dto.inventory.InventoryUsageRequest;
import com.trimly.backend.entity.InventoryItem;
import com.trimly.backend.entity.InventoryUsage;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.InventoryRepository;
import com.trimly.backend.repository.InventoryUsageRepository;
import com.trimly.backend.repository.ServiceRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryUsageRepository inventoryUsageRepository;
    private final ServiceRecordRepository serviceRecordRepository;
    private final ShopAccessService shopAccessService;

    @Transactional
    public InventoryResponse createItem(UUID shopId, InventoryRequest request, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        InventoryItem item = InventoryItem.builder()
                .shopId(shopId)
                .name(request.name())
                .description(request.description())
                .unit(request.unit())
                .quantityInStock(request.quantityInStock())
                .lowStockThreshold(request.lowStockThreshold())
                .costPerUnit(request.costPerUnit())
                .build();

        return toResponse(inventoryRepository.save(item));
    }

    @Transactional
    public InventoryResponse updateItem(UUID shopId, UUID itemId, InventoryRequest request, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        InventoryItem item = getItemOrThrow(shopId, itemId);
        item.setName(request.name());
        item.setDescription(request.description());
        item.setUnit(request.unit());
        item.setQuantityInStock(request.quantityInStock());
        item.setLowStockThreshold(request.lowStockThreshold());
        item.setCostPerUnit(request.costPerUnit());

        return toResponse(inventoryRepository.save(item));
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> listItems(UUID shopId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);
        return inventoryRepository.findByShopId(shopId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> listLowStock(UUID shopId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);
        return inventoryRepository.findByShopId(shopId).stream()
                .filter(i -> i.getLowStockThreshold() != null
                        && i.getQuantityInStock().compareTo(i.getLowStockThreshold()) <= 0)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteItem(UUID shopId, UUID itemId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);
        InventoryItem item = getItemOrThrow(shopId, itemId);
        inventoryRepository.delete(item);
    }

    @Transactional
    public void recordUsage(UUID shopId, UUID serviceRecordId,
                            List<InventoryUsageRequest> usages, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        serviceRecordRepository.findById(serviceRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("Service record not found."));

        for (InventoryUsageRequest u : usages) {
            InventoryItem item = getItemOrThrow(shopId, u.inventoryItemId());

            if (item.getQuantityInStock().compareTo(u.quantityUsed()) < 0) {
                throw new IllegalArgumentException(
                        "Insufficient stock for item: " + item.getName());
            }

            item.setQuantityInStock(item.getQuantityInStock().subtract(u.quantityUsed()));
            inventoryRepository.save(item);

            InventoryUsage usage = InventoryUsage.builder()
                    .serviceRecordId(serviceRecordId)
                    .inventoryItemId(u.inventoryItemId())
                    .quantityUsed(u.quantityUsed())
                    .build();

            inventoryUsageRepository.save(usage);
        }
    }

    private InventoryItem getItemOrThrow(UUID shopId, UUID itemId) {
        InventoryItem item = inventoryRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found."));
        if (!item.getShopId().equals(shopId)) {
            throw new ResourceNotFoundException("Inventory item not found.");
        }
        return item;
    }

    private InventoryResponse toResponse(InventoryItem i) {
        boolean lowStock = i.getLowStockThreshold() != null
                && i.getQuantityInStock().compareTo(i.getLowStockThreshold()) <= 0;
        return new InventoryResponse(
                i.getId(), i.getShopId(), i.getName(), i.getDescription(),
                i.getUnit(), i.getQuantityInStock(), i.getLowStockThreshold(),
                i.getCostPerUnit(), lowStock, i.getCreatedAt(), i.getUpdatedAt()
        );
    }
}
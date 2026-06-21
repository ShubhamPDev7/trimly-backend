package com.trimly.backend.service;

import com.trimly.backend.dto.service.ServiceItemRequest;
import com.trimly.backend.dto.service.ServiceItemResponse;
import com.trimly.backend.entity.ServiceItem;
import com.trimly.backend.enums.ServiceCategory;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.ServiceItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceItemServiceTest {

    @Mock private ServiceItemRepository serviceItemRepository;
    @Mock private ShopAccessService shopAccessService;

    @InjectMocks
    private ServiceItemService serviceItemService;

    private UUID shopId;
    private UUID currentUserId;
    private UUID serviceId;
    private ServiceItem serviceItem;
    private ServiceItemRequest request;

    @BeforeEach
    void setUp() {
        shopId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();
        serviceId = UUID.randomUUID();

        serviceItem = ServiceItem.builder()
                .id(serviceId)
                .shopId(shopId)
                .category(ServiceCategory.MALE)
                .name("Haircut")
                .price(new BigDecimal("200.00"))
                .estTimeMinutes(30)
                .deleted(false)
                .build();

        request = new ServiceItemRequest(ServiceCategory.MALE, "Haircut", new BigDecimal("200.00"), 30, null);
    }

    @Test
    void createService_savesAndReturnsResponse() {
        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(serviceItemRepository.save(any())).thenReturn(serviceItem);

        ServiceItemResponse response = serviceItemService.createService(shopId, request, currentUserId);

        assertThat(response.name()).isEqualTo("Haircut");
        assertThat(response.price()).isEqualByComparingTo(new BigDecimal("200.00"));
        verify(serviceItemRepository).save(any());
    }

    @Test
    void listServices_withoutCategory_returnsAll() {
        when(serviceItemRepository.findByShopIdAndDeletedFalse(shopId)).thenReturn(List.of(serviceItem));

        List<ServiceItemResponse> response = serviceItemService.listServices(shopId, null);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).name()).isEqualTo("Haircut");
    }

    @Test
    void listServices_withCategory_returnsFiltered() {
        when(serviceItemRepository.findByShopIdAndCategoryAndDeletedFalse(shopId, ServiceCategory.MALE))
                .thenReturn(List.of(serviceItem));

        List<ServiceItemResponse> response = serviceItemService.listServices(shopId, ServiceCategory.MALE);

        assertThat(response).hasSize(1);
    }

    @Test
    void updateService_updatesFieldsAndSaves() {
        ServiceItemRequest updateRequest = new ServiceItemRequest(ServiceCategory.FEMALE, "Facial", new BigDecimal("350.00"), 45, null);

        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(serviceItemRepository.findById(serviceId)).thenReturn(Optional.of(serviceItem));
        when(serviceItemRepository.save(serviceItem)).thenReturn(serviceItem);

        ServiceItemResponse response = serviceItemService.updateService(shopId, serviceId, updateRequest, currentUserId);

        assertThat(response.name()).isEqualTo("Facial");
        assertThat(response.price()).isEqualByComparingTo(new BigDecimal("350.00"));
        verify(serviceItemRepository).save(serviceItem);
    }

    @Test
    void updateService_notFound_throwsException() {
        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(serviceItemRepository.findById(serviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceItemService.updateService(shopId, serviceId, request, currentUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service not found");
    }

    @Test
    void updateService_deleted_throwsException() {
        serviceItem.setDeleted(true);

        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(serviceItemRepository.findById(serviceId)).thenReturn(Optional.of(serviceItem));

        assertThatThrownBy(() -> serviceItemService.updateService(shopId, serviceId, request, currentUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteService_softDeletesAndSaves() {
        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(serviceItemRepository.findById(serviceId)).thenReturn(Optional.of(serviceItem));
        when(serviceItemRepository.save(serviceItem)).thenReturn(serviceItem);

        serviceItemService.deleteService(shopId, serviceId, currentUserId);

        assertThat(serviceItem.isDeleted()).isTrue();
        assertThat(serviceItem.getDeletedAt()).isNotNull();
        verify(serviceItemRepository).save(serviceItem);
    }

    @Test
    void deleteService_alreadyDeleted_throwsException() {
        serviceItem.setDeleted(true);

        doNothing().when(shopAccessService).verifyShopAccess(currentUserId, shopId);
        when(serviceItemRepository.findById(serviceId)).thenReturn(Optional.of(serviceItem));

        assertThatThrownBy(() -> serviceItemService.deleteService(shopId, serviceId, currentUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
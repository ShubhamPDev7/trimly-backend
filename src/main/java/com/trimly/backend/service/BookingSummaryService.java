package com.trimly.backend.service;

import com.trimly.backend.dto.booking.BookingSummaryResponse;
import com.trimly.backend.dto.policy.CancellationPolicyResponse;
import com.trimly.backend.dto.service.ServiceItemResponse;
import com.trimly.backend.dto.shop.BarberPublicProfile;
import com.trimly.backend.entity.BarberProfile;
import com.trimly.backend.entity.Shop;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.BarberProfileRepository;
import com.trimly.backend.repository.ServiceItemRepository;
import com.trimly.backend.repository.ShopRepository;
import com.trimly.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingSummaryService {

    private final ShopRepository shopRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final BarberProfileRepository barberProfileRepository;
    private final UserRepository userRepository;
    private final BookingService bookingService;
    private final CancellationPolicyService cancellationPolicyService;

    @Transactional(readOnly = true)
    public BookingSummaryResponse getSummary(UUID shopId, UUID serviceId, UUID staffId, LocalDate date) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found."));

        var serviceEntity = serviceItemRepository.findById(serviceId)
                .filter(s -> s.getShopId().equals(shopId))
                .orElseThrow(() -> new ResourceNotFoundException("Service not found."));

        ServiceItemResponse service = new ServiceItemResponse(
                serviceEntity.getId(),
                serviceEntity.getShopId(),
                serviceEntity.getCategory(),
                serviceEntity.getName(),
                serviceEntity.getPrice(),
                serviceEntity.getEstTimeMinutes(),
                serviceEntity.getImageUrl()
        );

        BarberProfile profile = barberProfileRepository.findByShopIdAndUserId(shopId, staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found."));

        String staffName = userRepository.findById(staffId)
                .map(u -> u.getName())
                .orElse("Unknown");

        BarberPublicProfile staff = new BarberPublicProfile(
                profile.getUserId(),
                staffName,
                profile.getBio(),
                profile.getSpecialties(),
                profile.getExperienceYears(),
                profile.getInstagramHandle(),
                profile.getPhotoUrl()
        );

        List<LocalTime> slots = bookingService.getAvailableSlots(shopId, date, staffId).availableSlots();

        CancellationPolicyResponse policy = cancellationPolicyService.getPolicy(shopId).orElse(null);

        return new BookingSummaryResponse(shopId, shop.getName(), service, staff, date, slots, policy);
    }
}
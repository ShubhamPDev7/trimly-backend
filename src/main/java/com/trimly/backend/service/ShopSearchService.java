package com.trimly.backend.service;

import com.trimly.backend.dto.barber.BarberProfileResponse;
import com.trimly.backend.dto.hours.ShopHoursResponse;
import com.trimly.backend.dto.policy.CancellationPolicyResponse;
import com.trimly.backend.dto.service.ServiceItemResponse;
import com.trimly.backend.dto.shop.BarberPublicProfile;
import com.trimly.backend.dto.shop.ShopPublicProfileResponse;
import com.trimly.backend.dto.shop.ShopSearchResponse;
import com.trimly.backend.entity.Shop;
import com.trimly.backend.entity.ShopHours;
import com.trimly.backend.repository.BarberProfileRepository;
import com.trimly.backend.repository.ReviewRepository;
import com.trimly.backend.repository.ServiceItemRepository;
import com.trimly.backend.repository.ShopCancellationPolicyRepository;
import com.trimly.backend.repository.ShopHoursRepository;
import com.trimly.backend.repository.ShopRepository;
import com.trimly.backend.repository.ShopStaffRepository;
import com.trimly.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopSearchService {

    private final ShopRepository shopRepository;
    private final ReviewRepository reviewRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final ShopHoursRepository shopHoursRepository;
    private final BarberProfileRepository barberProfileRepository;
    private final UserRepository userRepository;
    private final ShopStaffRepository shopStaffRepository;
    private final ServiceItemService serviceItemService;
    private final ShopHoursService shopHoursService;
    private final ShopCancellationPolicyRepository cancellationPolicyRepository;
    private final CancellationPolicyService cancellationPolicyService;

    @Transactional(readOnly = true)
    public List<ShopSearchResponse> searchShops(String query, String locality) {
        List<Shop> shops = shopRepository.searchShops(
                query == null || query.isBlank() ? null : query.trim(),
                locality == null || locality.isBlank() ? null : locality.trim()
        );

        return shops.stream().map(this::toSearchResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<String> getLocalities() {
        return shopRepository.findAllLocalities();
    }

    @Transactional(readOnly = true)
    public ShopPublicProfileResponse getPublicProfile(UUID shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new com.trimly.backend.exception.ResourceNotFoundException("Shop not found."));

        Double avgRating = reviewRepository.findAverageRatingByShopId(shopId);
        long totalReviews = reviewRepository.countByShopId(shopId);

        List<ServiceItemResponse> services = serviceItemService.listServices(shopId, null);
        List<ShopHoursResponse> hours = shopHoursService.getHours(shopId);

        List<BarberPublicProfile> staff = barberProfileRepository.findByShopId(shopId)
                .stream()
                .map(p -> {
                    String name = userRepository.findById(p.getUserId())
                            .map(u -> u.getName())
                            .orElse("Unknown");
                    return new BarberPublicProfile(
                            p.getUserId(),
                            name,
                            p.getBio(),
                            p.getSpecialties(),
                            p.getExperienceYears(),
                            p.getInstagramHandle(),
                            p.getPhotoUrl()
                    );
                })
                .toList();

        CancellationPolicyResponse policy = cancellationPolicyService.getPolicy(shopId).orElse(null);

        return new ShopPublicProfileResponse(
                shop.getId(),
                shop.getName(),
                shop.getAddress(),
                shop.getLocality(),
                shop.getTimezone(),
                avgRating,
                (int) totalReviews,
                services,
                hours,
                staff,
                policy
        );
    }

    private ShopSearchResponse toSearchResponse(Shop shop) {
        Double avgRating = reviewRepository.findAverageRatingByShopId(shop.getId());
        long totalReviews = reviewRepository.countByShopId(shop.getId());
        long totalServices = serviceItemRepository.findByShopIdAndDeletedFalse(shop.getId()).size();
        boolean openNow = isOpenNow(shop);

        return new ShopSearchResponse(
                shop.getId(),
                shop.getName(),
                shop.getAddress(),
                shop.getLocality(),
                shop.getTimezone(),
                avgRating,
                (int) totalReviews,
                (int) totalServices,
                openNow
        );
    }

    private boolean isOpenNow(Shop shop) {
        try {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(shop.getTimezone()));
            int dayOfWeek = now.getDayOfWeek().getValue();
            LocalTime currentTime = now.toLocalTime();

            return shopHoursRepository.findByShopId(shop.getId()).stream()
                    .filter(h -> h.getDayOfWeek() == dayOfWeek && !h.isClosed())
                    .anyMatch(h -> !currentTime.isBefore(h.getOpenTime())
                            && !currentTime.isAfter(h.getCloseTime()));
        } catch (Exception e) {
            return false;
        }
    }
}
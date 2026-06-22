package com.trimly.backend.service;

import com.trimly.backend.dto.hours.ShopClosedDateRequest;
import com.trimly.backend.dto.hours.ShopClosedDateResponse;
import com.trimly.backend.dto.hours.ShopHoursRequest;
import com.trimly.backend.dto.hours.ShopHoursResponse;
import com.trimly.backend.entity.Shop;
import com.trimly.backend.entity.ShopClosedDate;
import com.trimly.backend.entity.ShopHours;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.ShopClosedDateRepository;
import com.trimly.backend.repository.ShopHoursRepository;
import com.trimly.backend.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShopHoursService {

    private final ShopHoursRepository shopHoursRepository;
    private final ShopClosedDateRepository shopClosedDateRepository;
    private final ShopRepository shopRepository;
    private final ShopAccessService shopAccessService;


    @Transactional
    public ShopHoursResponse setHours(UUID shopId, ShopHoursRequest request, UUID currentUserId) {
        shopAccessService.verifyShopOwner(currentUserId, shopId);

        if (!request.closed() && (request.openTime() == null || request.closeTime() == null)) {
            throw new IllegalArgumentException("openTime and closeTime are required when the shop is open.");
        }

        ShopHours hours = shopHoursRepository.findByShopIdAndDayOfWeek(shopId, request.dayOfWeek())
                .orElse(ShopHours.builder().shopId(shopId).dayOfWeek(request.dayOfWeek()).build());

        hours.setClosed(request.closed());
        hours.setOpenTime(request.closed() ? null : request.openTime());
        hours.setCloseTime(request.closed() ? null : request.closeTime());

        ShopHours saved = shopHoursRepository.save(hours);

        return toHoursResponse(saved);
    }

    public List<ShopHoursResponse> getHours(UUID shopId) {
        return shopHoursRepository.findByShopId(shopId).stream()
                .map(this::toHoursResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ShopClosedDateResponse addClosedDate(UUID shopId, ShopClosedDateRequest request, UUID currentUserId) {
        shopAccessService.verifyShopOwner(currentUserId, shopId);

        if (shopClosedDateRepository.existsByShopIdAndClosedDate(shopId, request.closedDate())) {
            throw new IllegalArgumentException("This date is already marked as closed.");
        }

        ShopClosedDate closedDate = ShopClosedDate.builder()
                .shopId(shopId)
                .closedDate(request.closedDate())
                .reason(request.reason())
                .build();

        ShopClosedDate saved = shopClosedDateRepository.save(closedDate);

        return toClosedDateResponse(saved);
    }

    @Transactional
    public void removeClosedDate(UUID shopId, UUID closedDateId, UUID currentUserId) {
        shopAccessService.verifyShopOwner(currentUserId, shopId);

        ShopClosedDate closedDate = shopClosedDateRepository.findById(closedDateId)
                .orElseThrow(() -> new ResourceNotFoundException("Closed date not found."));

        if (!closedDate.getShopId().equals(shopId)) {
            throw new ResourceNotFoundException("Closed date not found.");
        }

        shopClosedDateRepository.delete(closedDate);
    }

    public List<ShopClosedDateResponse> getClosedDates(UUID shopId) {
        return shopClosedDateRepository.findByShopIdAndClosedDateGreaterThanEqual(shopId, LocalDate.now()).stream()
                .map(this::toClosedDateResponse)
                .collect(Collectors.toList());
    }


    public boolean isOpenOn(UUID shopId, LocalDate date) {
        if (shopClosedDateRepository.existsByShopIdAndClosedDate(shopId, date)) {
            return false;
        }

        int dayOfWeek = date.getDayOfWeek().getValue();

        return shopHoursRepository.findByShopIdAndDayOfWeek(shopId, dayOfWeek)
                .map(h -> !h.isClosed())
                .orElse(false);
    }

    public Instant nextOpenInstant(UUID shopId, Instant from) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found."));

        ZoneId zone = ZoneId.of(shop.getTimezone());
        LocalDateTime localFrom = from.atZone(zone).toLocalDateTime();

        for (int i = 0; i < 14; i++) {
            LocalDate date = localFrom.toLocalDate();

            if (isOpenOn(shopId, date)) {
                int dayOfWeek = date.getDayOfWeek().getValue();
                ShopHours hours = shopHoursRepository.findByShopIdAndDayOfWeek(shopId, dayOfWeek).orElse(null);

                if (hours != null) {
                    LocalTime openTime = hours.getOpenTime();
                    LocalTime closeTime = hours.getCloseTime();
                    LocalTime currentTime = localFrom.toLocalTime();

                    if (currentTime.isBefore(closeTime)) {
                        LocalTime effectiveStart = currentTime.isBefore(openTime) ? openTime : currentTime;
                        return date.atTime(effectiveStart).atZone(zone).toInstant();
                    }
                }
            }

            localFrom = date.plusDays(1).atTime(LocalTime.MIDNIGHT);
        }

        return from.plusSeconds(14L * 24 * 60 * 60);
    }

    public Instant closingInstant(UUID shopId, LocalDate date) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found."));

        ZoneId zone = ZoneId.of(shop.getTimezone());

        if (!isOpenOn(shopId, date)) {
            return null;
        }

        int dayOfWeek = date.getDayOfWeek().getValue();

        return shopHoursRepository.findByShopIdAndDayOfWeek(shopId, dayOfWeek)
                .map(h -> date.atTime(h.getCloseTime()).atZone(zone).toInstant())
                .orElse(null);
    }

    private ShopHoursResponse toHoursResponse(ShopHours hours) {
        return new ShopHoursResponse(
                hours.getId(),
                hours.getShopId(),
                hours.getDayOfWeek(),
                hours.isClosed(),
                hours.getOpenTime(),
                hours.getCloseTime()
        );
    }

    private ShopClosedDateResponse toClosedDateResponse(ShopClosedDate closedDate) {
        return new ShopClosedDateResponse(
                closedDate.getId(),
                closedDate.getShopId(),
                closedDate.getClosedDate(),
                closedDate.getReason()
        );
    }

}
package com.trimly.backend.service;

import com.trimly.backend.dto.subscription.SubscriptionResponse;
import com.trimly.backend.entity.ShopSubscription;
import com.trimly.backend.enums.SubscriptionPlan;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.ShopSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final ShopSubscriptionRepository shopSubscriptionRepository;
    private final ShopAccessService shopAccessService;

    @Transactional
    public SubscriptionResponse getOrCreateSubscription(UUID shopId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);
        ShopSubscription sub = getOrCreateFree(shopId);
        return toResponse(sub);
    }

    @Transactional
    public SubscriptionResponse upgradePlan(UUID shopId, SubscriptionPlan plan, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        ShopSubscription sub = getOrCreateFree(shopId);
        sub.setPlan(plan);
        sub.setStatus("ACTIVE");
        sub.setStartedAt(Instant.now());

        if (plan == SubscriptionPlan.FREE) {
            sub.setExpiresAt(null);
        } else {
            sub.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        }

        return toResponse(shopSubscriptionRepository.save(sub));
    }

    @Transactional
    public void cancelSubscription(UUID shopId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        ShopSubscription sub = shopSubscriptionRepository.findByShopId(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found."));

        sub.setStatus("CANCELLED");
        sub.setPlan(SubscriptionPlan.FREE);
        sub.setExpiresAt(null);
        shopSubscriptionRepository.save(sub);
    }

    public SubscriptionPlan getPlan(UUID shopId) {
        return shopSubscriptionRepository.findByShopId(shopId)
                .filter(ShopSubscription::isActive)
                .map(ShopSubscription::getPlan)
                .orElse(SubscriptionPlan.FREE);
    }

    public void enforceStaffLimit(UUID shopId, int currentStaffCount) {
        SubscriptionPlan plan = getPlan(shopId);
        if (!plan.isUnlimited() && currentStaffCount >= plan.getMaxStaff()) {
            throw new IllegalStateException(
                    "Staff limit reached for your " + plan.name() + " plan. Upgrade to add more staff.");
        }
    }

    public void enforceBookingLimit(UUID shopId, int currentMonthBookings) {
        SubscriptionPlan plan = getPlan(shopId);
        if (!plan.isUnlimited() && currentMonthBookings >= plan.getMaxBookingsPerMonth()) {
            throw new IllegalStateException(
                    "Monthly booking limit reached for your " + plan.name() + " plan. Upgrade to accept more bookings.");
        }
    }

    public void enforceDashboardAccess(UUID shopId) {
        SubscriptionPlan plan = getPlan(shopId);
        if (!plan.isDashboardEnabled()) {
            throw new IllegalStateException(
                    "Dashboard is not available on the " + plan.name() + " plan. Upgrade to PRO or higher.");
        }
    }

    private ShopSubscription getOrCreateFree(UUID shopId) {
        return shopSubscriptionRepository.findByShopId(shopId)
                .orElseGet(() -> shopSubscriptionRepository.save(
                        ShopSubscription.builder()
                                .shopId(shopId)
                                .plan(SubscriptionPlan.FREE)
                                .status("ACTIVE")
                                .startedAt(Instant.now())
                                .build()));
    }

    private SubscriptionResponse toResponse(ShopSubscription sub) {
        SubscriptionPlan plan = sub.getPlan();
        return new SubscriptionResponse(
                sub.getId(),
                sub.getShopId(),
                plan,
                sub.getStatus(),
                sub.isActive(),
                plan.getMaxStaff(),
                plan.getMaxBookingsPerMonth(),
                plan.isDashboardEnabled(),
                plan.isAnalyticsEnabled(),
                plan.isMultibranchEnabled(),
                sub.getStartedAt(),
                sub.getExpiresAt()
        );
    }
}
package com.trimly.backend.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.trimly.backend.dto.subscription.SubscriptionOrderResponse;
import com.trimly.backend.dto.subscription.SubscriptionResponse;
import com.trimly.backend.entity.ShopSubscription;
import com.trimly.backend.enums.SubscriptionPlan;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.ShopSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final ShopSubscriptionRepository shopSubscriptionRepository;
    private final ShopAccessService shopAccessService;

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    private RazorpayClient razorpayClient;

    @PostConstruct
    public void init() throws RazorpayException {
        this.razorpayClient = new RazorpayClient(keyId, keySecret);
    }

    private static final java.util.Map<String, Long> PLAN_PRICES_PAISE = java.util.Map.of(
            "PRO", 99900L,
            "ENTERPRISE", 299900L
    );

    @Transactional
    public SubscriptionResponse getOrCreateSubscription(UUID shopId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);
        ShopSubscription sub = getOrCreateFree(shopId);
        return toResponse(sub);
    }

    @Transactional
    public SubscriptionOrderResponse createPaymentOrder(UUID shopId, SubscriptionPlan plan, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        if (plan == SubscriptionPlan.FREE) {
            throw new IllegalArgumentException("Cannot create payment order for FREE plan.");
        }

        Long amountPaise = PLAN_PRICES_PAISE.get(plan.name());
        if (amountPaise == null) throw new IllegalArgumentException("Unknown plan: " + plan.name());

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "sub_" + shopId.toString().substring(0, 8));

            Order order = razorpayClient.orders.create(orderRequest);
            String orderId = order.get("id");

            ShopSubscription sub = getOrCreateFree(shopId);
            sub.setRazorpayOrderId(orderId);
            sub.setPendingPlan(plan.name());
            shopSubscriptionRepository.save(sub);

            return new SubscriptionOrderResponse(orderId, amountPaise, "INR", plan.name(), shopId.toString());
        } catch (RazorpayException e) {
            throw new RuntimeException("Failed to create Razorpay order: " + e.getMessage());
        }
    }

    @Transactional
    public void activateFromPayment(String razorpayOrderId) {
        shopSubscriptionRepository.findByRazorpayOrderId(razorpayOrderId).ifPresent(sub -> {
            if (sub.getPendingPlan() == null) return;
            SubscriptionPlan plan = SubscriptionPlan.valueOf(sub.getPendingPlan());
            sub.setPlan(plan);
            sub.setStatus("ACTIVE");
            sub.setStartedAt(java.time.Instant.now());
            sub.setExpiresAt(java.time.Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS));
            sub.setPendingPlan(null);
            shopSubscriptionRepository.save(sub);
        });
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
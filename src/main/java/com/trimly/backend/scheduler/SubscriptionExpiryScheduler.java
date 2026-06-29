package com.trimly.backend.scheduler;

import com.trimly.backend.entity.ShopSubscription;
import com.trimly.backend.enums.SubscriptionPlan;
import com.trimly.backend.repository.ShopSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryScheduler {

    private final ShopSubscriptionRepository shopSubscriptionRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void downgradeExpiredSubscriptions() {
        List<ShopSubscription> expired = shopSubscriptionRepository.findExpiredSubscriptions(Instant.now());

        for (ShopSubscription sub : expired) {
            log.info("Downgrading shop {} from {} to FREE — subscription expired at {}",
                    sub.getShopId(), sub.getPlan(), sub.getExpiresAt());

            sub.setPlan(SubscriptionPlan.FREE);
            sub.setStatus("EXPIRED");
            sub.setExpiresAt(null);
            shopSubscriptionRepository.save(sub);
        }

        if (!expired.isEmpty()) {
            log.info("Downgraded {} expired subscriptions to FREE.", expired.size());
        }
    }
}
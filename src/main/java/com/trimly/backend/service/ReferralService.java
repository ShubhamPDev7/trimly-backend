package com.trimly.backend.service;

import com.trimly.backend.dto.referral.MyReferralCodeResponse;
import com.trimly.backend.dto.referral.ReferralResponse;
import com.trimly.backend.entity.Referral;
import com.trimly.backend.entity.User;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.ReferralRepository;
import com.trimly.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReferralService {

    private static final int REFERRAL_POINTS = 100;

    private final ReferralRepository referralRepository;
    private final UserRepository userRepository;
    private final LoyaltyService loyaltyService;
    private final ShopAccessService shopAccessService;

    @Transactional
    public MyReferralCodeResponse getMyReferralCode(UUID shopId, User currentUser) {
        if (currentUser.getReferralCode() == null) {
            String code = generateCode(currentUser);
            currentUser.setReferralCode(code);
            userRepository.save(currentUser);
        }

        String message = "Book your next haircut at this shop and get loyalty points! Use my code: "
                + currentUser.getReferralCode();

        return new MyReferralCodeResponse(currentUser.getReferralCode(), message);
    }

    @Transactional
    public void applyReferralCode(UUID shopId, String referralCode, User currentUser) {
        if (referralRepository.existsByShopIdAndReferredId(shopId, currentUser.getId())) {
            throw new IllegalStateException("You have already used a referral code for this shop.");
        }

        Referral referral = referralRepository
                .findByReferralCodeAndShopId(referralCode, shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid referral code."));

        if (referral.getReferrerId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("You cannot use your own referral code.");
        }

        referral.setReferredId(currentUser.getId());
        referral.setStatus("PENDING");
        referralRepository.save(referral);
    }

    @Transactional
    public void completeReferral(UUID shopId, UUID referredUserId) {
        referralRepository.findByShopIdAndReferrerId(shopId, referredUserId)
                .stream()
                .filter(r -> "PENDING".equals(r.getStatus())
                        && r.getReferredId().equals(referredUserId))
                .findFirst()
                .ifPresent(referral -> {
                    referral.setStatus("COMPLETED");
                    referral.setPointsAwarded(REFERRAL_POINTS);
                    referral.setCompletedAt(Instant.now());
                    referralRepository.save(referral);

                    loyaltyService.awardReferralPoints(shopId, referral.getReferrerId(), REFERRAL_POINTS);
                });
    }

    @Transactional(readOnly = true)
    public List<ReferralResponse> getMyReferrals(UUID shopId, UUID currentUserId) {
        return referralRepository.findByShopIdAndReferrerId(shopId, currentUserId)
                .stream().map(this::toResponse).toList();
    }

    private String generateCode(User user) {
        String base = user.getName().replaceAll("\\s+", "").toUpperCase();
        if (base.length() > 6) base = base.substring(0, 6);
        return base + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private ReferralResponse toResponse(Referral r) {
        return new ReferralResponse(
                r.getId(), r.getShopId(), r.getReferrerId(), r.getReferredId(),
                r.getReferralCode(), r.getStatus(), r.getPointsAwarded(),
                r.getCreatedAt(), r.getCompletedAt()
        );
    }
}
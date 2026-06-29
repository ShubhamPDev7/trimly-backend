package com.trimly.backend.service;

import com.trimly.backend.dto.policy.CancellationPolicyRequest;
import com.trimly.backend.dto.policy.CancellationPolicyResponse;
import com.trimly.backend.entity.ShopCancellationPolicy;
import com.trimly.backend.repository.ShopCancellationPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CancellationPolicyService {

    private final ShopCancellationPolicyRepository policyRepository;
    private final ShopAccessService shopAccessService;

    @Transactional
    public CancellationPolicyResponse upsertPolicy(UUID shopId, CancellationPolicyRequest request, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        ShopCancellationPolicy policy = policyRepository.findByShopId(shopId)
                .orElse(ShopCancellationPolicy.builder().shopId(shopId).build());

        policy.setMinHoursBeforeCancel(request.minHoursBeforeCancel());
        return toResponse(policyRepository.save(policy));
    }

    @Transactional(readOnly = true)
    public Optional<CancellationPolicyResponse> getPolicy(UUID shopId) {
        return policyRepository.findByShopId(shopId).map(this::toResponse);
    }

    @Transactional
    public void deletePolicy(UUID shopId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);
        policyRepository.deleteByShopId(shopId);
    }

    private CancellationPolicyResponse toResponse(ShopCancellationPolicy policy) {
        int hours = policy.getMinHoursBeforeCancel();
        String description = "Cancel at least " + hours + " hour" + (hours == 1 ? "" : "s") +
                " before your appointment or cancellation will not be allowed.";
        return new CancellationPolicyResponse(
                policy.getId(),
                policy.getShopId(),
                hours,
                description,
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}
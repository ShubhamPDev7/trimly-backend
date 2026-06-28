package com.trimly.backend.service;

import com.trimly.backend.dto.loyalty.LoyaltyAccountResponse;
import com.trimly.backend.dto.loyalty.LoyaltyTransactionResponse;
import com.trimly.backend.dto.loyalty.RedeemPointsRequest;
import com.trimly.backend.entity.Bill;
import com.trimly.backend.entity.LoyaltyAccount;
import com.trimly.backend.entity.LoyaltyTransaction;
import com.trimly.backend.enums.LoyaltyTransactionType;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.BillRepository;
import com.trimly.backend.repository.LoyaltyAccountRepository;
import com.trimly.backend.repository.LoyaltyTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoyaltyService {

    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final BillRepository billRepository;
    private final ShopAccessService shopAccessService;

    private static final int POINTS_PER_RUPEE = 1;
    private static final int POINTS_PER_REDEMPTION_UNIT = 100;
    private static final int RUPEES_PER_REDEMPTION_UNIT = 10;

    @Transactional
    public void awardPoints(UUID shopId, UUID customerId, UUID billId, BigDecimal totalAmount) {
        if (customerId == null) return;

        int pointsEarned = totalAmount.intValue() * POINTS_PER_RUPEE;
        if (pointsEarned <= 0) return;

        LoyaltyAccount account = loyaltyAccountRepository
                .findByShopIdAndCustomerId(shopId, customerId)
                .orElseGet(() -> LoyaltyAccount.builder()
                        .shopId(shopId)
                        .customerId(customerId)
                        .build());

        account.setBalance(account.getBalance() + pointsEarned);
        LoyaltyAccount savedAccount = loyaltyAccountRepository.save(account);

        LoyaltyTransaction transaction = LoyaltyTransaction.builder()
                .loyaltyAccountId(savedAccount.getId())
                .shopId(shopId)
                .customerId(customerId)
                .type(LoyaltyTransactionType.EARNED)
                .points(pointsEarned)
                .billId(billId)
                .description("Earned " + pointsEarned + " points for bill #" + billId)
                .build();

        loyaltyTransactionRepository.save(transaction);
    }

    @Transactional
    public LoyaltyAccountResponse redeemPoints(UUID shopId, RedeemPointsRequest request, UUID currentUserId) {
        if (request.pointsToRedeem() % POINTS_PER_REDEMPTION_UNIT != 0) {
            throw new IllegalArgumentException(
                    "Points must be redeemed in multiples of " + POINTS_PER_REDEMPTION_UNIT + ".");
        }

        Bill bill = billRepository.findById(request.billId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found."));

        if (!bill.getShopId().equals(shopId)) {
            throw new ResourceNotFoundException("Bill not found.");
        }

        LoyaltyAccount account = loyaltyAccountRepository
                .findByShopIdAndCustomerId(shopId, currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("No loyalty account found for this shop."));

        if (account.getBalance() < request.pointsToRedeem()) {
            throw new IllegalArgumentException(
                    "Insufficient points. You have " + account.getBalance() + " points.");
        }

        int discount = (request.pointsToRedeem() / POINTS_PER_REDEMPTION_UNIT) * RUPEES_PER_REDEMPTION_UNIT;

        account.setBalance(account.getBalance() - request.pointsToRedeem());
        LoyaltyAccount savedAccount = loyaltyAccountRepository.save(account);

        LoyaltyTransaction transaction = LoyaltyTransaction.builder()
                .loyaltyAccountId(savedAccount.getId())
                .shopId(shopId)
                .customerId(currentUserId)
                .type(LoyaltyTransactionType.REDEEMED)
                .points(-request.pointsToRedeem())
                .billId(request.billId())
                .description("Redeemed " + request.pointsToRedeem() + " points for ₹" + discount + " discount on bill #" + request.billId())
                .build();

        loyaltyTransactionRepository.save(transaction);

        return toAccountResponse(savedAccount);
    }

    public LoyaltyAccountResponse getAccount(UUID shopId, UUID currentUserId) {
        LoyaltyAccount account = loyaltyAccountRepository
                .findByShopIdAndCustomerId(shopId, currentUserId)
                .orElse(LoyaltyAccount.builder()
                        .shopId(shopId)
                        .customerId(currentUserId)
                        .balance(0)
                        .build());

        return toAccountResponse(account);
    }

    public List<LoyaltyTransactionResponse> getTransactions(UUID shopId, UUID currentUserId) {
        LoyaltyAccount account = loyaltyAccountRepository
                .findByShopIdAndCustomerId(shopId, currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("No loyalty account found for this shop."));

        return loyaltyTransactionRepository
                .findByLoyaltyAccountIdOrderByCreatedAtDesc(account.getId())
                .stream()
                .map(this::toTransactionResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void awardReferralPoints(UUID shopId, UUID referrerId, int points) {
        LoyaltyAccount account = loyaltyAccountRepository
                .findByShopIdAndCustomerId(shopId, referrerId)
                .orElseGet(() -> loyaltyAccountRepository.save(
                        LoyaltyAccount.builder().shopId(shopId).customerId(referrerId).balance(0).build()));

        account.setBalance(account.getBalance() + points);
        loyaltyAccountRepository.save(account);

        loyaltyTransactionRepository.save(LoyaltyTransaction.builder()
                .loyaltyAccountId(account.getId())
                .shopId(shopId)
                .customerId(referrerId)
                .type(LoyaltyTransactionType.EARNED)
                .points(points)
                .description("Referral bonus")
                .build());
    }

    private LoyaltyAccountResponse toAccountResponse(LoyaltyAccount account) {
        int balanceInRupees = (account.getBalance() / POINTS_PER_REDEMPTION_UNIT) * RUPEES_PER_REDEMPTION_UNIT;
        return new LoyaltyAccountResponse(
                account.getId(),
                account.getShopId(),
                account.getCustomerId(),
                account.getBalance(),
                balanceInRupees,
                account.getUpdatedAt()
        );
    }

    private LoyaltyTransactionResponse toTransactionResponse(LoyaltyTransaction transaction) {
        return new LoyaltyTransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getPoints(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }

}
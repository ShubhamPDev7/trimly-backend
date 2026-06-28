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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyServiceTest {

    @Mock private LoyaltyAccountRepository loyaltyAccountRepository;
    @Mock private LoyaltyTransactionRepository loyaltyTransactionRepository;
    @Mock private BillRepository billRepository;
    @Mock private ShopAccessService shopAccessService;

    @InjectMocks
    private LoyaltyService loyaltyService;

    private UUID shopId;
    private UUID customerId;
    private UUID billId;
    private LoyaltyAccount account;

    @BeforeEach
    void setUp() {
        shopId     = UUID.randomUUID();
        customerId = UUID.randomUUID();
        billId     = UUID.randomUUID();

        account = LoyaltyAccount.builder()
                .id(UUID.randomUUID())
                .shopId(shopId)
                .customerId(customerId)
                .balance(500)
                .build();
    }

    @Test
    void awardPoints_newAccount_createsAccountAndTransaction() {
        when(loyaltyAccountRepository.findByShopIdAndCustomerId(shopId, customerId))
                .thenReturn(Optional.empty());
        when(loyaltyAccountRepository.save(any())).thenReturn(account);
        when(loyaltyTransactionRepository.save(any())).thenReturn(new LoyaltyTransaction());

        loyaltyService.awardPoints(shopId, customerId, billId, new BigDecimal("300.00"));

        verify(loyaltyAccountRepository).save(any(LoyaltyAccount.class));
        verify(loyaltyTransactionRepository).save(any(LoyaltyTransaction.class));
    }

    @Test
    void awardPoints_existingAccount_addsToBalance() {
        account.setBalance(100);

        when(loyaltyAccountRepository.findByShopIdAndCustomerId(shopId, customerId))
                .thenReturn(Optional.of(account));
        when(loyaltyAccountRepository.save(any())).thenReturn(account);
        when(loyaltyTransactionRepository.save(any())).thenReturn(new LoyaltyTransaction());

        loyaltyService.awardPoints(shopId, customerId, billId, new BigDecimal("200.00"));

        assertThat(account.getBalance()).isEqualTo(300);
    }

    @Test
    void awardPoints_nullCustomerId_doesNothing() {
        loyaltyService.awardPoints(shopId, null, billId, new BigDecimal("300.00"));

        verify(loyaltyAccountRepository, never()).save(any());
    }

    @Test
    void awardPoints_zeroAmount_doesNothing() {
        loyaltyService.awardPoints(shopId, customerId, billId, BigDecimal.ZERO);

        verify(loyaltyAccountRepository, never()).save(any());
    }

    @Test
    void redeemPoints_success_deductsBalanceAndSavesTransaction() {
        UUID billId2 = UUID.randomUUID();
        Bill bill = Bill.builder().id(billId2).shopId(shopId).build();
        RedeemPointsRequest request = new RedeemPointsRequest(billId2, 200);

        when(billRepository.findById(billId2)).thenReturn(Optional.of(bill));
        when(loyaltyAccountRepository.findByShopIdAndCustomerId(shopId, customerId))
                .thenReturn(Optional.of(account));
        when(loyaltyAccountRepository.save(any())).thenReturn(account);
        when(loyaltyTransactionRepository.save(any())).thenReturn(new LoyaltyTransaction());

        LoyaltyAccountResponse response = loyaltyService.redeemPoints(shopId, request, customerId);

        assertThat(account.getBalance()).isEqualTo(300);
        verify(loyaltyTransactionRepository).save(any(LoyaltyTransaction.class));
    }

    @Test
    void redeemPoints_notMultipleOf100_throws() {
        RedeemPointsRequest request = new RedeemPointsRequest(billId, 150);

        assertThatThrownBy(() -> loyaltyService.redeemPoints(shopId, request, customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiples of 100");
    }

    @Test
    void redeemPoints_insufficientBalance_throws() {
        UUID billId2 = UUID.randomUUID();
        Bill bill = Bill.builder().id(billId2).shopId(shopId).build();
        account.setBalance(50);
        RedeemPointsRequest request = new RedeemPointsRequest(billId2, 100);

        when(billRepository.findById(billId2)).thenReturn(Optional.of(bill));
        when(loyaltyAccountRepository.findByShopIdAndCustomerId(shopId, customerId))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> loyaltyService.redeemPoints(shopId, request, customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient points");
    }

    @Test
    void redeemPoints_billNotFound_throws() {
        RedeemPointsRequest request = new RedeemPointsRequest(billId, 100);

        when(billRepository.findById(billId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loyaltyService.redeemPoints(shopId, request, customerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void redeemPoints_billBelongsToDifferentShop_throws() {
        Bill bill = Bill.builder().id(billId).shopId(UUID.randomUUID()).build();
        RedeemPointsRequest request = new RedeemPointsRequest(billId, 100);

        when(billRepository.findById(billId)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> loyaltyService.redeemPoints(shopId, request, customerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAccount_existingAccount_returnsBalance() {
        when(loyaltyAccountRepository.findByShopIdAndCustomerId(shopId, customerId))
                .thenReturn(Optional.of(account));

        LoyaltyAccountResponse response = loyaltyService.getAccount(shopId, customerId);

        assertThat(response.balance()).isEqualTo(500);
    }

    @Test
    void getAccount_noAccount_returnsZeroBalance() {
        when(loyaltyAccountRepository.findByShopIdAndCustomerId(shopId, customerId))
                .thenReturn(Optional.empty());

        LoyaltyAccountResponse response = loyaltyService.getAccount(shopId, customerId);

        assertThat(response.balance()).isEqualTo(0);
    }

    @Test
    void getTransactions_returnsHistory() {
        UUID accountId = account.getId();
        LoyaltyTransaction tx = LoyaltyTransaction.builder()
                .id(UUID.randomUUID())
                .loyaltyAccountId(accountId)
                .type(LoyaltyTransactionType.EARNED)
                .points(100)
                .description("Earned 100 points")
                .build();

        when(loyaltyAccountRepository.findByShopIdAndCustomerId(shopId, customerId))
                .thenReturn(Optional.of(account));
        when(loyaltyTransactionRepository.findByLoyaltyAccountIdOrderByCreatedAtDesc(accountId))
                .thenReturn(List.of(tx));

        List<LoyaltyTransactionResponse> result = loyaltyService.getTransactions(shopId, customerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).points()).isEqualTo(100);
        assertThat(result.get(0).type()).isEqualTo(LoyaltyTransactionType.EARNED);
    }

    @Test
    void getTransactions_noAccount_throws() {
        when(loyaltyAccountRepository.findByShopIdAndCustomerId(shopId, customerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> loyaltyService.getTransactions(shopId, customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No loyalty account");
    }
}
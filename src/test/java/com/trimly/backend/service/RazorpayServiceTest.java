package com.trimly.backend.service;

import com.trimly.backend.dto.razorpay.RazorpayOrderResponse;
import com.trimly.backend.entity.Bill;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.enums.PaymentMode;
import com.trimly.backend.enums.PaymentStatus;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.BillRepository;
import com.trimly.backend.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RazorpayServiceTest {

    @Mock private BillRepository billRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private LoyaltyService loyaltyService;

    @InjectMocks
    private RazorpayService razorpayService;

    private UUID billId;
    private UUID shopId;
    private UUID bookingId;
    private UUID customerId;
    private Bill pendingBill;

    @BeforeEach
    void setUp() {
        billId     = UUID.randomUUID();
        shopId     = UUID.randomUUID();
        bookingId  = UUID.randomUUID();
        customerId = UUID.randomUUID();

        ReflectionTestUtils.setField(razorpayService, "keyId",         "rzp_test_key");
        ReflectionTestUtils.setField(razorpayService, "keySecret",     "test_secret");
        ReflectionTestUtils.setField(razorpayService, "webhookSecret", "webhook_secret");

        pendingBill = Bill.builder()
                .id(billId)
                .shopId(shopId)
                .bookingId(bookingId)
                .totalAmount(new BigDecimal("500.00"))
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMode(PaymentMode.RAZORPAY)
                .build();
    }

    @Test
    void handleCapturedPayment_marksBillPaidAndAwardsPoints() {
        String razorpayOrderId  = "order_test123";
        String razorpayPaymentId = "pay_test456";

        pendingBill.setRazorpayOrderId(razorpayOrderId);

        Booking booking = Booking.builder()
                .id(bookingId)
                .shopId(shopId)
                .customerId(customerId)
                .build();

        when(billRepository.findByRazorpayOrderId(razorpayOrderId))
                .thenReturn(Optional.of(pendingBill));
        when(billRepository.save(any())).thenReturn(pendingBill);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        razorpayService.handleCapturedPayment(razorpayOrderId, razorpayPaymentId);

        assertThat(pendingBill.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(pendingBill.getRazorpayPaymentId()).isEqualTo(razorpayPaymentId);
        verify(billRepository).save(pendingBill);
        verify(loyaltyService).awardPoints(
                eq(shopId), eq(customerId), eq(billId), any(BigDecimal.class));
    }

    @Test
    void handleCapturedPayment_billNotFound_throws() {
        when(billRepository.findByRazorpayOrderId("order_missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                razorpayService.handleCapturedPayment("order_missing", "pay_xyz"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void handleCapturedPayment_alreadyPaid_isIdempotent() {
        pendingBill.setPaymentStatus(PaymentStatus.PAID);
        pendingBill.setRazorpayOrderId("order_paid");

        when(billRepository.findByRazorpayOrderId("order_paid"))
                .thenReturn(Optional.of(pendingBill));

        razorpayService.handleCapturedPayment("order_paid", "pay_duplicate");

        verify(billRepository, never()).save(any());
        verify(loyaltyService, never()).awardPoints(any(), any(), any(), any());
    }

    @Test
    void handleCapturedPayment_walkInBill_noLoyaltyAwarded() {
        String razorpayOrderId = "order_walkin";
        Bill walkInBill = Bill.builder()
                .id(billId)
                .shopId(shopId)
                .bookingId(null)
                .totalAmount(new BigDecimal("300.00"))
                .paymentStatus(PaymentStatus.PENDING)
                .razorpayOrderId(razorpayOrderId)
                .build();

        when(billRepository.findByRazorpayOrderId(razorpayOrderId))
                .thenReturn(Optional.of(walkInBill));
        when(billRepository.save(any())).thenReturn(walkInBill);

        razorpayService.handleCapturedPayment(razorpayOrderId, "pay_walkin");

        assertThat(walkInBill.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(loyaltyService, never()).awardPoints(any(), any(), any(), any());
    }

    @Test
    void createOrder_billAlreadyPaid_throws() {
        pendingBill.setPaymentStatus(PaymentStatus.PAID);
        when(billRepository.findById(billId)).thenReturn(Optional.of(pendingBill));

        assertThatThrownBy(() -> razorpayService.createOrder(billId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already paid");
    }

    @Test
    void createOrder_billNotFound_throws() {
        when(billRepository.findById(billId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> razorpayService.createOrder(billId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
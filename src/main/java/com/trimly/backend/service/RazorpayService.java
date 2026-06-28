package com.trimly.backend.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.trimly.backend.dto.razorpay.RazorpayOrderResponse;
import com.trimly.backend.dto.razorpay.RazorpayWebhookPayload;
import com.trimly.backend.entity.Bill;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.enums.PaymentMode;
import com.trimly.backend.enums.PaymentStatus;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.BillRepository;
import com.trimly.backend.repository.BookingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayService {

    private final BillRepository billRepository;
    private final BookingRepository bookingRepository;
    private final LoyaltyService loyaltyService;

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    private RazorpayClient razorpayClient;

    @PostConstruct
    public void init() throws RazorpayException {
        this.razorpayClient = new RazorpayClient(keyId, keySecret);
    }

    /**
     * Creates a Razorpay order for an existing bill.
     * The bill must already exist (created when booking was confirmed / checked in).
     */
    @Transactional
    public RazorpayOrderResponse createOrder(UUID billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));

        if (bill.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Bill is already paid.");
        }

        try {
            // Razorpay expects amount in paise (1 INR = 100 paise)
            int amountInPaise = bill.getTotalAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .intValue();

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            // Razorpay receipt max length is 40 chars; use last 36 chars of UUID
            orderRequest.put("receipt", billId.toString().replace("-", "").substring(0, 32));
            orderRequest.put("payment_capture", 1); // auto-capture

            Order order = razorpayClient.orders.create(orderRequest);
            String razorpayOrderId = order.get("id");

            // Persist the order ID on the bill so we can match the webhook later
            bill.setRazorpayOrderId(razorpayOrderId);
            bill.setPaymentMode(PaymentMode.RAZORPAY);
            billRepository.save(bill);

            log.info("Created Razorpay order {} for bill {}", razorpayOrderId, billId);

            return new RazorpayOrderResponse(
                    billId,
                    razorpayOrderId,
                    bill.getTotalAmount(),
                    "INR",
                    keyId
            );

        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order for bill {}: {}", billId, e.getMessage());
            throw new RuntimeException("Payment gateway error. Please try again.", e);
        }
    }

    /**
     * Handles the payment.captured webhook from Razorpay.
     * Verifies HMAC signature, marks bill PAID, awards loyalty points.
     */
    @Transactional
    public void handleWebhook(String rawBody, String razorpaySignature) {
        // 1. Verify webhook signature
        if (!isValidSignature(rawBody, razorpaySignature)) {
            log.warn("Razorpay webhook signature verification failed.");
            throw new SecurityException("Invalid webhook signature.");
        }

        // 2. Parse payload (basic JSON — no need for ObjectMapper here since we use Jackson DTO)
        // We'll rely on the controller to deserialize the payload and pass event + entity to us
        // so this method only gets called for payment.captured events.
        // See handleCapturedPayment() below.
    }

    /**
     * Called after signature verification, for payment.captured events only.
     */
    @Transactional
    public void handleCapturedPayment(String razorpayOrderId, String razorpayPaymentId) {
        Bill bill = billRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> {
                    log.error("No bill found for Razorpay order {}", razorpayOrderId);
                    return new ResourceNotFoundException("Bill not found for order: " + razorpayOrderId);
                });

        if (bill.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("Bill {} already marked PAID — skipping duplicate webhook.", bill.getId());
            return; // idempotent
        }

        bill.setPaymentStatus(PaymentStatus.PAID);
        bill.setRazorpayPaymentId(razorpayPaymentId);
        billRepository.save(bill);

        // Award loyalty points if this was a customer booking (not walk-in)
        if (bill.getBookingId() != null) {
            try {
                loyaltyService.awardPoints(
                        bill.getShopId(),
                        getCustomerIdFromBill(bill),
                        bill.getId(),
                        bill.getTotalAmount()
                );
            } catch (Exception e) {
                // Don't fail payment confirmation just because loyalty award fails
                log.error("Failed to award loyalty points for bill {}: {}", bill.getId(), e.getMessage());
            }
        }

        log.info("Bill {} marked PAID via Razorpay payment {}", bill.getId(), razorpayPaymentId);
    }

    /**
     * Verifies the X-Razorpay-Signature header using HMAC-SHA256.
     */
    public boolean isValidSignature(String payload, String signature) {
        try {
            return Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (RazorpayException e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Fetches customerId via the booking linked to the bill.
     * Returns null for walk-in bills (no booking).
     */
    private UUID getCustomerIdFromBill(Bill bill) {
        if (bill.getBookingId() == null) return null;
        return bookingRepository.findById(bill.getBookingId())
                .map(Booking::getCustomerId)
                .orElse(null);
    }
}
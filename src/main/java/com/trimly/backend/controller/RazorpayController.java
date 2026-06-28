package com.trimly.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trimly.backend.dto.razorpay.RazorpayOrderResponse;
import com.trimly.backend.dto.razorpay.RazorpayWebhookPayload;
import com.trimly.backend.service.RazorpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RazorpayController {

    private final RazorpayService razorpayService;
    private final ObjectMapper objectMapper;


    @PostMapping("/bills/{billId}/pay/online")
    public ResponseEntity<RazorpayOrderResponse> createOnlineOrder(
            @PathVariable UUID billId
    ) {
        RazorpayOrderResponse response = razorpayService.createOrder(billId);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/razorpay/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) {
        if (signature == null || signature.isBlank()) {
            log.warn("Razorpay webhook received without signature header — rejecting.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }


//        if (!razorpayService.isValidSignature(rawBody, signature)) {
//            log.warn("Razorpay webhook signature invalid — rejecting.");
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
//        }

        try {
            RazorpayWebhookPayload payload = objectMapper.readValue(rawBody, RazorpayWebhookPayload.class);


            if ("payment.captured".equals(payload.event())) {
                String orderId   = payload.payload().payment().entity().orderId();
                String paymentId = payload.payload().payment().entity().id();
                razorpayService.handleCapturedPayment(orderId, paymentId);
            } else {
                log.debug("Ignoring Razorpay event: {}", payload.event());
            }

        } catch (Exception e) {

            log.error("Error processing Razorpay webhook: {}", e.getMessage(), e);
        }


        return ResponseEntity.ok().build();
    }
}
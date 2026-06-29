package com.trimly.backend.service;

import com.trimly.backend.dto.auth.AuthResponse;
import com.trimly.backend.entity.User;
import com.trimly.backend.enums.Role;
import com.trimly.backend.repository.UserRepository;
import com.trimly.backend.security.JwtUtil;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final AuthService authService;

    @Value("${twilio.verify.service-sid}")
    private String verifyServiceSid;

    public void sendOtp(String phone) {
        String normalized = normalizePhone(phone);
        log.info("Sending OTP to: {}", normalized);
        Verification.creator(verifyServiceSid, normalized, "sms").create();
    }

    @Transactional
    public AuthResponse verifyOtp(String phone, String code) {
        String normalized = normalizePhone(phone);

        VerificationCheck check = VerificationCheck.creator(verifyServiceSid)
                .setTo(normalized)
                .setCode(code)
                .create();

        if (!"approved".equals(check.getStatus())) {
            throw new IllegalArgumentException("Invalid or expired OTP.");
        }

        User user = userRepository.findByPhone(normalized)
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("User")
                        .phone(normalized)
                        .role(Role.CUSTOMER)
                        .build()));

        String accessToken = jwtUtil.generateToken(
                user.getId(),
                user.getPhone(),
                user.getRole().name(),
                List.of()
        );
        String refreshToken = authService.issueRefreshToken(user.getId());

        return new AuthResponse(accessToken, refreshToken, user.getId(), user.getName(), user.getPhone(), user.getRole().name());
    }

    private String normalizePhone(String phone) {
        if (phone == null) throw new IllegalArgumentException("Phone number is required.");
        String trimmed = phone.trim();
        if (!trimmed.startsWith("+")) {
            trimmed = "+" + trimmed;
        }
        return trimmed;
    }
}
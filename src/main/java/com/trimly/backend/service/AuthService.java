package com.trimly.backend.service;

import com.trimly.backend.dto.auth.AuthResponse;
import com.trimly.backend.dto.auth.ForgotPasswordRequest;
import com.trimly.backend.dto.auth.LoginRequest;
import com.trimly.backend.dto.auth.MessageResponse;
import com.trimly.backend.dto.auth.RefreshTokenRequest;
import com.trimly.backend.dto.auth.RegisterRequest;
import com.trimly.backend.dto.auth.ResetPasswordRequest;
import com.trimly.backend.entity.PasswordResetToken;
import com.trimly.backend.entity.RefreshToken;
import com.trimly.backend.entity.ShopStaff;
import com.trimly.backend.entity.User;
import com.trimly.backend.enums.Role;
import com.trimly.backend.repository.PasswordResetTokenRepository;
import com.trimly.backend.repository.RefreshTokenRepository;
import com.trimly.backend.repository.ShopStaffRepository;
import com.trimly.backend.repository.UserRepository;
import com.trimly.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ShopStaffRepository shopStaffRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    @Value("${app.frontend.reset-password-url}")
    private String resetPasswordBaseUrl;

    @Value("${trimly.refresh-token.expiration-ms}")
    private long refreshTokenExpirationMs;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (request.role() == Role.STAFF) {
            throw new IllegalArgumentException("Staff accounts must be created by a shop owner, not self-registered");
        }

        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        User user = User.builder()
                .name(request.name())
                .email(normalizedEmail)
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();

        user = userRepository.save(user);

        String accessToken = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name(), List.of());
        String refreshToken = issueRefreshToken(user.getId());

        return toAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {

        String normalizedEmail = request.email().trim().toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));

        List<UUID> shopIds = shopStaffRepository.findByUserId(user.getId()).stream()
                .map(ShopStaff::getShopId)
                .collect(Collectors.toList());

        String accessToken = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name(), shopIds);
        String refreshToken = issueRefreshToken(user.getId());

        return toAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {

        String tokenHash = hashToken(request.refreshToken());

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token."));

        if (storedToken.isRevoked()) {
            revokeAllTokensForUser(storedToken.getUserId());
            throw new IllegalArgumentException("Refresh token has already been used. All sessions have been logged out for security - please log in again.");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token has expired. Please log in again.");
        }

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token."));

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        List<UUID> shopIds = shopStaffRepository.findByUserId(user.getId()).stream()
                .map(ShopStaff::getShopId)
                .collect(Collectors.toList());

        String newAccessToken = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name(), shopIds);
        String newRefreshToken = issueRefreshToken(user.getId());

        return toAuthResponse(user, newAccessToken, newRefreshToken);
    }

    @Transactional
    public MessageResponse logout(RefreshTokenRequest request) {

        String tokenHash = hashToken(request.refreshToken());

        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });

        return new MessageResponse("Logged out successfully.");
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {

        String normalizedEmail = request.email().trim().toLowerCase();

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            String rawToken = generateRawToken();
            String tokenHash = hashToken(rawToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .userId(user.getId())
                    .tokenHash(tokenHash)
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .build();

            passwordResetTokenRepository.save(resetToken);

            String resetLink = resetPasswordBaseUrl + "?token=" + rawToken;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
        }

        return new MessageResponse(
                "If an account exists with that email, a password reset link has been sent.");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {

        String tokenHash = hashToken(request.token());

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token."));

        if (resetToken.isUsed()) {
            throw new IllegalArgumentException("Invalid or expired reset token.");
        }

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Invalid or expired reset token.");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token."));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        revokeAllTokensForUser(user.getId());

        return new MessageResponse("Password has been reset successfully.");
    }

    String issueRefreshToken(UUID userId) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(refreshTokenExpirationMs, ChronoUnit.MILLIS))
                .build();

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    private void revokeAllTokensForUser(UUID userId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
        activeTokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(activeTokens);
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private AuthResponse toAuthResponse(User user, String accessToken, String refreshToken) {
        return new AuthResponse(accessToken, refreshToken, user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }

}
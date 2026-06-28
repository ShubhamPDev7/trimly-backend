package com.trimly.backend.service;

import com.trimly.backend.dto.auth.*;
import com.trimly.backend.entity.PasswordResetToken;
import com.trimly.backend.entity.RefreshToken;
import com.trimly.backend.entity.ShopStaff;
import com.trimly.backend.entity.User;
import com.trimly.backend.enums.Role;
import com.trimly.backend.repository.*;
import com.trimly.backend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ShopStaffRepository shopStaffRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    private UUID userId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        ReflectionTestUtils.setField(authService, "resetPasswordBaseUrl", "http://localhost:5173/reset-password");
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationMs", 2592000000L);

        testUser = new User();
        testUser.setId(userId);
        testUser.setName("Shubham");
        testUser.setEmail("shubham@test.com");
        testUser.setRole(Role.CUSTOMER);
        testUser.setPasswordHash("hashed");
    }

    @Test
    void register_success_returnsAuthResponse() {
        RegisterRequest request = new RegisterRequest("Shubham", "shubham@test.com", "9999999999", "password123", Role.CUSTOMER);

        when(userRepository.existsByEmail("shubham@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(testUser);
        when(shopStaffRepository.findByUserId(userId)).thenReturn(List.of());
        when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("access-token");
        when(refreshTokenRepository.save(any())).thenReturn(new RefreshToken());

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.email()).isEqualTo("shubham@test.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_emailAlreadyExists_throws() {
        RegisterRequest request = new RegisterRequest("Shubham", "shubham@test.com", "9999999999", "password123", Role.CUSTOMER);

        when(userRepository.existsByEmail("shubham@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void register_staffSelfRegistration_throws() {
        RegisterRequest request = new RegisterRequest("Staff", "staff@test.com", "9999999999", "password123", Role.STAFF);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shop owner");
    }

    @Test
    void login_success_returnsAuthResponse() {
        LoginRequest request = new LoginRequest("shubham@test.com", "password123");

        when(authenticationManager.authenticate(any())).thenReturn(mock(UsernamePasswordAuthenticationToken.class));
        when(userRepository.findByEmail("shubham@test.com")).thenReturn(Optional.of(testUser));
        when(shopStaffRepository.findByUserId(userId)).thenReturn(List.of());
        when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("access-token");
        when(refreshTokenRepository.save(any())).thenReturn(new RefreshToken());

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("access-token");
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void refreshToken_validToken_returnsNewTokens() {
        String rawToken = "valid-raw-token";

        RefreshToken storedToken = RefreshToken.builder()
                .userId(userId)
                .revoked(false)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(shopStaffRepository.findByUserId(userId)).thenReturn(List.of());
        when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("new-access-token");
        when(refreshTokenRepository.save(any())).thenReturn(new RefreshToken());

        AuthResponse response = authService.refreshToken(new RefreshTokenRequest(rawToken));

        assertThat(response.token()).isEqualTo("new-access-token");
        assertThat(storedToken.isRevoked()).isTrue();
    }

    @Test
    void refreshToken_revokedToken_revokesAllAndThrows() {
        RefreshToken revokedToken = RefreshToken.builder()
                .userId(userId)
                .revoked(true)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revokedToken));
        when(refreshTokenRepository.findByUserIdAndRevokedFalse(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("reused-token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void refreshToken_expiredToken_throws() {
        RefreshToken expiredToken = RefreshToken.builder()
                .userId(userId)
                .revoked(false)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("expired-token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void logout_revokesToken() {
        RefreshToken token = RefreshToken.builder()
                .userId(userId).revoked(false)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any())).thenReturn(token);

        MessageResponse response = authService.logout(new RefreshTokenRequest("some-token"));

        assertThat(response.message()).containsIgnoringCase("logged out");
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    void forgotPassword_userExists_sendsEmail() {
        when(userRepository.findByEmail("shubham@test.com")).thenReturn(Optional.of(testUser));
        when(passwordResetTokenRepository.save(any())).thenReturn(new PasswordResetToken());
        doNothing().when(emailService).sendPasswordResetEmail(anyString(), anyString());

        MessageResponse response = authService.forgotPassword(new ForgotPasswordRequest("shubham@test.com"));

        assertThat(response.message()).contains("If an account exists");
        verify(emailService).sendPasswordResetEmail(eq("shubham@test.com"), anyString());
    }

    @Test
    void forgotPassword_userNotFound_returnsSameMessageNoEmail() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        MessageResponse response = authService.forgotPassword(new ForgotPasswordRequest("unknown@test.com"));

        assertThat(response.message()).contains("If an account exists");
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void resetPassword_validToken_updatesPassword() {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(userId)
                .used(false)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newpassword")).thenReturn("newhashed");
        when(userRepository.save(any())).thenReturn(testUser);
        when(passwordResetTokenRepository.save(any())).thenReturn(resetToken);
        when(refreshTokenRepository.findByUserIdAndRevokedFalse(userId)).thenReturn(List.of());

        MessageResponse response = authService.resetPassword(new ResetPasswordRequest("valid-token", "newpassword"));

        assertThat(response.message()).containsIgnoringCase("reset successfully");
        assertThat(testUser.getPasswordHash()).isEqualTo("newhashed");
        assertThat(resetToken.isUsed()).isTrue();
    }

    @Test
    void resetPassword_expiredToken_throws() {
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .userId(userId)
                .used(false)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("expired", "newpassword")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void resetPassword_alreadyUsedToken_throws() {
        PasswordResetToken usedToken = PasswordResetToken.builder()
                .userId(userId)
                .used(true)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("used-token", "newpassword")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }
}
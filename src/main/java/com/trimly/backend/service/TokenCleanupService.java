package com.trimly.backend.service;

import com.trimly.backend.repository.PasswordResetTokenRepository;
import com.trimly.backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    // Runs once a day at 3 AM server time
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();

        refreshTokenRepository.deleteByExpiresAtBeforeOrRevokedTrue(now);
        passwordResetTokenRepository.deleteByExpiresAtBeforeOrUsedTrue(now);

        log.info("Token cleanup job ran at {}", now);
    }

}
package com.trimly.backend.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.trimly.backend.dto.fcm.FcmTokenRequest;
import com.trimly.backend.entity.FcmToken;
import com.trimly.backend.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final FcmTokenRepository fcmTokenRepository;

    @Transactional
    public void registerToken(UUID userId, FcmTokenRequest request) {
        fcmTokenRepository.findByUserIdAndToken(userId, request.token())
                .ifPresentOrElse(
                        existing -> {},
                        () -> fcmTokenRepository.save(FcmToken.builder()
                                .userId(userId)
                                .token(request.token())
                                .deviceType(request.deviceType())
                                .build())
                );
    }

    @Transactional
    public void removeToken(UUID userId, String token) {
        fcmTokenRepository.deleteByUserIdAndToken(userId, token);
    }

    public void sendToUser(UUID userId, String title, String body) {
        List<FcmToken> tokens = fcmTokenRepository.findByUserId(userId);
        for (FcmToken fcmToken : tokens) {
            try {
                Message message = Message.builder()
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .setToken(fcmToken.getToken())
                        .build();
                FirebaseMessaging.getInstance().send(message);
            } catch (Exception e) {
                log.warn("Failed to send FCM to token {}: {}", fcmToken.getToken(), e.getMessage());
            }
        }
    }
}
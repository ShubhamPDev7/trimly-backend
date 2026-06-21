package com.trimly.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class EmailService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${resend.api.key}")
    private String resendApiKey;

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(resendApiKey);

        Map<String, Object> body = Map.of(
                "from", "Trimly <onboarding@resend.dev>",
                "to", new String[]{toEmail},
                "subject", "Reset your Trimly password",
                "html", "<p>Click the link below to reset your password. This link expires in 1 hour.</p>"
                        + "<p><a href=\"" + resetLink + "\">Reset Password</a></p>"
                        + "<p>If you didn't request this, you can safely ignore this email.</p>"
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        restTemplate.postForEntity("https://api.resend.com/emails", request, String.class);
    }

}
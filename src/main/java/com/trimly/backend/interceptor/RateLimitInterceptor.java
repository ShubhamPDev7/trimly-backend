package com.trimly.backend.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trimly.backend.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ip = getClientIp(request);
        String uri = request.getRequestURI();
        String method = request.getMethod();

        String key = null;
        int maxRequests = 0;
        Duration window = null;

        if ("POST".equals(method)) {
            if (uri.endsWith("/api/v1/auth/login")) {
                key = "rate:login:" + ip;
                maxRequests = 10;
                window = Duration.ofMinutes(15);
            } else if (uri.endsWith("/api/v1/auth/forgot-password")) {
                key = "rate:forgot-password:" + ip;
                maxRequests = 5;
                window = Duration.ofHours(1);
            } else if (uri.endsWith("/api/v1/auth/register")) {
                key = "rate:register:" + ip;
                maxRequests = 5;
                window = Duration.ofHours(1);
            } else if (uri.endsWith("/api/v1/auth/send-otp")) {
                key = "rate:send-otp:" + ip;
                maxRequests = 5;
                window = Duration.ofMinutes(15);
            } else if (uri.endsWith("/walk-in-queue")) {
                key = "rate:walk-in-queue:" + ip;
                maxRequests = 20;
                window = Duration.ofHours(1);
            }
        }

        if (key == null) {
            return true;
        }

        if (!rateLimitService.isAllowed(key, maxRequests, window)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(),
                    Map.of("error", "Too many requests. Please try again later."));
            return false;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
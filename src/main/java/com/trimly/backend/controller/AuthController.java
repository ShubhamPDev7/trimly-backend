package com.trimly.backend.controller;

import com.trimly.backend.dto.auth.AuthResponse;
import com.trimly.backend.dto.auth.LoginRequest;
import com.trimly.backend.dto.auth.RegisterRequest;
import com.trimly.backend.entity.ShopStaff;
import com.trimly.backend.entity.User;
import com.trimly.backend.enums.Role;
import com.trimly.backend.repository.ShopStaffRepository;
import com.trimly.backend.repository.UserRepository;
import com.trimly.backend.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final ShopStaffRepository shopStaffRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {

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

        user =  userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name(), List.of());

        return ResponseEntity.status(HttpStatus.CREATED).body(toAuthResponse(user, token));

    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {

        String normalizedEmail = request.email().trim().toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));

        List<UUID> shopIds = shopStaffRepository.findByUserId(user.getId()).stream()
                .map(ShopStaff::getShopId)
                .collect(Collectors.toList());

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name(), shopIds);

        return ResponseEntity.ok(toAuthResponse(user, token));

    }

    private AuthResponse toAuthResponse(User user, String token) {
        return new AuthResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }

}

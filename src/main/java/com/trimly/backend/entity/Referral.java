package com.trimly.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "referrals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Referral implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "referrer_id", nullable = false)
    private UUID referrerId;

    @Column(name = "referred_id", nullable = false)
    private UUID referredId;

    @Column(name = "referral_code", nullable = false, length = 20)
    private String referralCode;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "points_awarded", nullable = false)
    private Integer pointsAwarded;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
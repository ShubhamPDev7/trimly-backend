package com.trimly.backend.entity;

import com.trimly.backend.enums.LoyaltyTransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "loyalty_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyTransaction implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "loyalty_account_id", nullable = false)
    private UUID loyaltyAccountId;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private LoyaltyTransactionType type;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "bill_id")
    private UUID billId;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
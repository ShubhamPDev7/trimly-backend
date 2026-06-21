package com.trimly.backend.entity;


import com.trimly.backend.enums.WalkInStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "walk_in_queue_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalkInQueueEntry implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "guest_name")
    private String guestName;

    @Column(name = "guest_phone")
    private String guestPhone;

    @Column(name = "preferred_staff_id")
    private UUID preferredStaffId;

    @Column(name = "assigned_staff_id")
    private UUID assignedStaffId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WalkInStatus status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = Instant.now();
    }

}

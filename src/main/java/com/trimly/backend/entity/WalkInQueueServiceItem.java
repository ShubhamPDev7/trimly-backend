package com.trimly.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "walk_in_queue_service_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalkInQueueServiceItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "queue_entry_id", nullable = false)
    private UUID queueEntryId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "price_at_join", nullable = false)
    private BigDecimal priceAtJoin;
}

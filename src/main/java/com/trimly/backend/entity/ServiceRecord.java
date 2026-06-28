package com.trimly.backend.entity;

import com.trimly.backend.converter.StringListConverter;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "service_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceRecord implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "walk_in_queue_entry_id")
    private UUID walkInQueueEntryId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Convert(converter = StringListConverter.class)
    @Column(name = "products_used", columnDefinition = "TEXT")
    private List<String> productsUsed;

    @Convert(converter = StringListConverter.class)
    @Column(name = "photo_urls", columnDefinition = "TEXT")
    private List<String> photoUrls;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
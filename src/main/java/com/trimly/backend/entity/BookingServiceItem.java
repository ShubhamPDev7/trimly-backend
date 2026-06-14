package com.trimly.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "booking_services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingServiceItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @NotNull
    @PositiveOrZero
    @Column(name = "price_at_booking", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtBooking;
}

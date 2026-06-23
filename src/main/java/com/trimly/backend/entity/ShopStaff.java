package com.trimly.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.trimly.backend.enums.StaffRole;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "shop_staff", uniqueConstraints = {
        @UniqueConstraint(name = "uk_shop_staff_shop_user", columnNames = {"shop_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopStaff implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_in_shop", nullable = false)
    private StaffRole roleInShop;
}
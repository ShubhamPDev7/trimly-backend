package com.trimly.backend.repository;

import com.trimly.backend.entity.BarberProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BarberProfileRepository extends JpaRepository<BarberProfile, UUID> {

    Optional<BarberProfile> findByShopIdAndUserId(UUID shopId, UUID userId);

    List<BarberProfile> findByShopId(UUID shopId);
}
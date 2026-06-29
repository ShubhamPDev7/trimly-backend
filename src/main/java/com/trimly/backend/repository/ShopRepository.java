package com.trimly.backend.repository;

import com.trimly.backend.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ShopRepository extends JpaRepository<Shop, UUID> {

    List<Shop> findByOwnerId(UUID ownerId);

    List<Shop> findByOwnerIdAndDeletedFalse(UUID ownerId);

    @Query("SELECT DISTINCT s FROM Shop s WHERE " +
            "(:query IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))) AND " +
            "(:locality IS NULL OR LOWER(s.locality) LIKE LOWER(CONCAT('%', CAST(:locality AS string), '%')))")
    List<Shop> searchShops(@Param("query") String query, @Param("locality") String locality);

    @Query("SELECT DISTINCT s.locality FROM Shop s WHERE s.locality IS NOT NULL ORDER BY s.locality")
    List<String> findAllLocalities();

}
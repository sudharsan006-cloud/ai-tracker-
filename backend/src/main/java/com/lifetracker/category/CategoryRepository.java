package com.lifetracker.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c WHERE c.isActive = true AND (c.userId IS NULL OR c.userId = :userId) ORDER BY c.name ASC")
    List<Category> findVisibleCategories(@Param("userId") UUID userId);

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.isActive = true AND lower(c.name) = lower(:name) AND (c.userId IS NULL OR c.userId = :userId)")
    boolean existsByNameIgnoreCaseAndVisibleToUser(@Param("name") String name, @Param("userId") UUID userId);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.isActive = true AND lower(c.name) = lower(:name) AND (c.userId IS NULL OR c.userId = :userId) AND c.id <> :excludeId")
    boolean existsByNameIgnoreCaseAndVisibleToUserExcluding(@Param("name") String name, @Param("userId") UUID userId, @Param("excludeId") UUID excludeId);

    Optional<Category> findFirstByUserIdIsNullAndNameIgnoreCase(String name);
}

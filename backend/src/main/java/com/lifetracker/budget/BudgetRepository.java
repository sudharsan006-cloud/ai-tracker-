package com.lifetracker.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByUserIdAndIsActiveTrue(UUID userId);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndCategoryIdIsNullAndCurrencyIdAndIsActiveTrue(UUID userId, Integer currencyId);

    boolean existsByUserIdAndCategoryIdAndCurrencyIdAndIsActiveTrue(UUID userId, UUID categoryId, Integer currencyId);
}

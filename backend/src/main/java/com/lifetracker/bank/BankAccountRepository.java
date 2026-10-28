package com.lifetracker.bank;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {
    List<BankAccount> findByUserIdAndIsActiveTrue(UUID userId);
    Optional<BankAccount> findByIdAndUserId(UUID id, UUID userId);
    long countByUserIdAndIsActiveTrue(UUID userId);
}

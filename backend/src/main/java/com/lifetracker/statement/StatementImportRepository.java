package com.lifetracker.statement;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StatementImportRepository extends JpaRepository<StatementImport, UUID> {

    boolean existsByUserIdAndStatus(UUID userId, String status);

    List<StatementImport> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<StatementImport> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("DELETE FROM StatementImport s WHERE s.userId = :userId AND s.status <> 'committed' AND s.createdAt < :cutoff")
    int deleteExpiredImports(@Param("userId") UUID userId, @Param("cutoff") Instant cutoff);
}

package com.lifetracker.statement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StatementImportRowRepository extends JpaRepository<StatementImportRow, UUID> {

    List<StatementImportRow> findByImportIdOrderByRowIndexAsc(UUID importId);

    Optional<StatementImportRow> findByIdAndImportId(UUID id, UUID importId);

    @Query("SELECT COUNT(r) FROM StatementImportRow r WHERE r.importId = :importId AND r.selected = true")
    long countSelectedByImportId(@Param("importId") UUID importId);

    @Query("SELECT COUNT(r) FROM StatementImportRow r WHERE r.importId = :importId AND r.selected = true AND (r.needsCheck = true OR r.txnDate IS NULL OR r.amount IS NULL OR r.direction IS NULL)")
    long countBlockingByImportId(@Param("importId") UUID importId);

    @Modifying
    @Query("DELETE FROM StatementImportRow r WHERE r.importId = :importId")
    void deleteByImportId(@Param("importId") UUID importId);
}

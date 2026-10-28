package com.lifetracker.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {

    Optional<BankTransaction> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndSmsFingerprint(UUID userId, String smsFingerprint);

    List<BankTransaction> findByReviewPartnerId(UUID reviewPartnerId);

    @Query("SELECT t FROM BankTransaction t WHERE t.userId = :userId AND t.reviewStatus = 'possible_duplicate' AND cast(t.id as string) < cast(t.reviewPartnerId as string)")
    List<BankTransaction> findDuplicatePairs(@Param("userId") UUID userId);

    @Query("SELECT t FROM BankTransaction t WHERE t.userId = :userId AND t.reviewStatus = 'possible_self_transfer' AND cast(t.id as string) < cast(t.reviewPartnerId as string)")
    List<BankTransaction> findSelfTransferPairs(@Param("userId") UUID userId);

    @Query("SELECT t FROM BankTransaction t WHERE t.userId = :userId AND t.reviewStatus = 'possible_refund_match'")
    List<BankTransaction> findRefundMatches(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE BankTransaction t SET t.reviewStatus = 'none', t.reviewPartnerId = null WHERE t.reviewPartnerId = :id")
    int clearReviewFlagsForPartner(@Param("id") UUID id);
}

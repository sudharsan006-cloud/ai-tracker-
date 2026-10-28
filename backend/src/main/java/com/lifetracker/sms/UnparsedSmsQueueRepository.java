package com.lifetracker.sms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnparsedSmsQueueRepository extends JpaRepository<UnparsedSmsQueue, UUID> {

    List<UnparsedSmsQueue> findByUserIdAndStatusOrderByReceivedAtDesc(UUID userId, String status);

    Optional<UnparsedSmsQueue> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndSmsFingerprint(UUID userId, String smsFingerprint);
}

package com.emailnotification.adapter.out.persistence

import com.emailnotification.domain.model.EmailStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

/**
 * Spring Data JPA repository for [RetryControlJpaEntity].
 *
 * Used exclusively by [RetryControlPersistenceAdapter].
 */
interface RetryControlJpaRepository : JpaRepository<RetryControlJpaEntity, Long> {

    /** Looks up the retry record linked to the given [emailRequestId] (internal PK). */
    fun findByEmailRequestId(emailRequestId: Long): Optional<RetryControlJpaEntity>

    /**
     * Returns retry records whose nextAttemptAt is on or before [now],
     * whose linked EmailRequest still has the given [status],
     * **and** whose lockedUntil is either null or has already expired.
     *
     * Already-claimed rows are excluded so the scheduler avoids passing them
     * to ProcessRetryUseCase only to be skipped there.
     */
    @Query("""
        SELECT r FROM RetryControlJpaEntity r
        WHERE r.nextAttemptAt <= :now
        AND (r.lockedUntil IS NULL OR r.lockedUntil < :now)
        AND r.emailRequestId IN (
            SELECT e.id FROM EmailRequestJpaEntity e WHERE e.status = :status
        )
    """)
    fun findAllReadyForRetry(
        @Param("now") now: LocalDateTime,
        @Param("status") status: EmailStatus
    ): List<RetryControlJpaEntity>

    /**
     * Atomically claims the row with [id] by setting locked_until = [lockExpiry],
     * but only when the row is currently unclaimed.
     *
     * The inner `FOR UPDATE SKIP LOCKED` ensures competing instances return 0
     * immediately instead of blocking on the PostgreSQL row lock.
     *
     * @return number of rows updated (1 = claimed, 0 = already held by another instance).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE retry_control
        SET locked_until = :lockExpiry
        WHERE id IN (
            SELECT id FROM retry_control
            WHERE id        = :id
            AND  (locked_until IS NULL OR locked_until < :now)
            FOR UPDATE SKIP LOCKED
        )
    """, nativeQuery = true)
    fun tryClaimForProcessing(
        @Param("id") id: Long,
        @Param("lockExpiry") lockExpiry: LocalDateTime,
        @Param("now") now: LocalDateTime
    ): Int
}

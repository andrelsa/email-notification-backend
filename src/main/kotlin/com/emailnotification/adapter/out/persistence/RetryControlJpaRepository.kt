package com.emailnotification.adapter.out.persistence

import com.emailnotification.domain.model.EmailStatus
import org.springframework.data.jpa.repository.JpaRepository
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
     * Returns retry records whose nextAttemptAt is on or before [now]
     * and whose linked EmailRequest still has the given [status].
     */
    @Query("""
        SELECT r FROM RetryControlJpaEntity r
        WHERE r.nextAttemptAt <= :now
        AND r.emailRequestId IN (
            SELECT e.id FROM EmailRequestJpaEntity e WHERE e.status = :status
        )
    """)
    fun findAllReadyForRetry(
        @Param("now") now: LocalDateTime,
        @Param("status") status: EmailStatus
    ): List<RetryControlJpaEntity>
}

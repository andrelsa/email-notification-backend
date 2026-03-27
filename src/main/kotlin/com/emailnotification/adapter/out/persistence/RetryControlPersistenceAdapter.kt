package com.emailnotification.adapter.out.persistence

import com.emailnotification.domain.model.EmailStatus
import com.emailnotification.domain.model.RetryControl
import com.emailnotification.domain.port.RetryControlRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Output adapter — implements [RetryControlRepository] using Spring Data JPA.
 *
 * Dependency direction: adapter -> domain port (never the other way around).
 */
@Component
class RetryControlPersistenceAdapter(
    private val jpaRepository: RetryControlJpaRepository,
    private val mapper: RetryControlMapper
) : RetryControlRepository {

    override fun save(retryControl: RetryControl): RetryControl {
        val entity = mapper.toEntity(retryControl)
        return mapper.toDomain(jpaRepository.save(entity))
    }

    override fun findByEmailRequestId(emailRequestId: Long): RetryControl? =
        jpaRepository.findByEmailRequestId(emailRequestId)
            .map(mapper::toDomain)
            .orElse(null)

    /**
     * Fetches retry records whose nextAttemptAt has elapsed, whose linked EmailRequest
     * is still [EmailStatus.RETRYING], and whose lockedUntil has not yet kicked in.
     */
    override fun findAllReadyForRetry(now: LocalDateTime): List<RetryControl> =
        jpaRepository.findAllReadyForRetry(now, EmailStatus.RETRYING)
            .map(mapper::toDomain)

    /**
     * Atomically claims the retry record for exclusive processing using a
     * `UPDATE … FOR UPDATE SKIP LOCKED` pattern.
     *
     * Runs in a **dedicated REQUIRES_NEW transaction** that commits before this method
     * returns.  This means:
     * - The PostgreSQL row lock is released immediately after the UPDATE commits,
     *   not held for the duration of the (potentially slow) email send.
     * - Other instances see the committed [RetryControl.lockedUntil] value and skip
     *   the row without blocking.
     *
     * @return `true` when this instance won the claim (rows-updated == 1).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun tryClaimForProcessing(id: Long, lockExpiry: LocalDateTime): Boolean =
        jpaRepository.tryClaimForProcessing(id, lockExpiry, LocalDateTime.now()) > 0
}

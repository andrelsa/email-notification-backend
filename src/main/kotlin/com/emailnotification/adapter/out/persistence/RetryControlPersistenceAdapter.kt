package com.emailnotification.adapter.out.persistence

import com.emailnotification.domain.model.EmailStatus
import com.emailnotification.domain.model.RetryControl
import com.emailnotification.domain.port.RetryControlRepository
import org.springframework.stereotype.Component
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
     * Fetches retry records whose nextAttemptAt has elapsed and whose
     * linked EmailRequest is still [EmailStatus.RETRYING].
     */
    override fun findAllReadyForRetry(now: LocalDateTime): List<RetryControl> =
        jpaRepository.findAllReadyForRetry(now, EmailStatus.RETRYING)
            .map(mapper::toDomain)
}

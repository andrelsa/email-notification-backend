package com.emailnotification.adapter.out.persistence

import com.emailnotification.domain.model.RetryControl
import org.springframework.stereotype.Component

/**
 * Maps between [RetryControlJpaEntity] and [com.emailnotification.domain.model.RetryControl].
 */
@Component
class RetryControlMapper {

    /** Converts a JPA entity row into a pure domain [RetryControl]. */
    fun toDomain(entity: RetryControlJpaEntity): RetryControl = RetryControl(
        id               = entity.id,
        emailRequestId   = entity.emailRequestId,
        attemptCount     = entity.attemptCount,
        maxAttempts      = entity.maxAttempts,
        lastAttemptAt    = entity.lastAttemptAt,
        nextAttemptAt    = entity.nextAttemptAt,
        lastErrorMessage = entity.lastErrorMessage,
        lockedUntil      = entity.lockedUntil,
        createdAt        = entity.createdAt,
        updatedAt        = entity.updatedAt
    )

    /**
     * Converts a domain [RetryControl] into a persistable [RetryControlJpaEntity].
     */
    fun toEntity(domain: RetryControl): RetryControlJpaEntity = RetryControlJpaEntity(
        id               = domain.id,
        emailRequestId   = domain.emailRequestId,
        attemptCount     = domain.attemptCount,
        maxAttempts      = domain.maxAttempts,
        lastAttemptAt    = domain.lastAttemptAt,
        nextAttemptAt    = domain.nextAttemptAt,
        lastErrorMessage = domain.lastErrorMessage,
        lockedUntil      = domain.lockedUntil,
        createdAt        = domain.createdAt,
        updatedAt        = domain.updatedAt
    )
}

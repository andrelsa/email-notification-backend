package com.emailnotification.adapter.out.persistence

import com.emailnotification.domain.model.EmailStatusEntry
import org.springframework.stereotype.Component

/**
 * Maps between [EmailStatusEntryJpaEntity] and [com.emailnotification.domain.model.EmailStatusEntry].
 */
@Component
class EmailStatusEntryMapper {

    /** Converts a JPA entity row into a pure domain [EmailStatusEntry]. */
    fun toDomain(entity: EmailStatusEntryJpaEntity): EmailStatusEntry = EmailStatusEntry(
        id             = entity.id,
        emailRequestId = entity.emailRequestId,
        previousStatus = entity.previousStatus,
        newStatus      = entity.newStatus,
        description    = entity.description,
        occurredAt     = entity.occurredAt
    )

    /**
     * Converts a domain [EmailStatusEntry] into a persistable [EmailStatusEntryJpaEntity].
     */
    fun toEntity(domain: EmailStatusEntry): EmailStatusEntryJpaEntity = EmailStatusEntryJpaEntity(
        id             = domain.id,
        emailRequestId = domain.emailRequestId,
        previousStatus = domain.previousStatus,
        newStatus      = domain.newStatus,
        description    = domain.description,
        occurredAt     = domain.occurredAt
    )
}

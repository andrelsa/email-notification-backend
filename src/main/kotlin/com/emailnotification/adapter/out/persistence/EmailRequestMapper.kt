package com.emailnotification.adapter.out.persistence

import com.emailnotification.domain.model.EmailRequest
import com.emailnotification.domain.model.EmailRequestId
import org.springframework.stereotype.Component

/**
 * Maps between [EmailRequestJpaEntity] and [com.emailnotification.domain.model.EmailRequest].
 */
@Component
class EmailRequestMapper {

    /** Converts a JPA entity row into a pure domain [EmailRequest]. */
    fun toDomain(entity: EmailRequestJpaEntity): EmailRequest = EmailRequest(
        id             = entity.id,
        publicId       = EmailRequestId.from(entity.publicId),
        userId         = entity.userId,
        recipientEmail = entity.recipientEmail,
        subject        = entity.subject,
        body           = entity.body,
        eventType      = entity.eventType,
        status         = entity.status,
        createdAt      = entity.createdAt,
        updatedAt      = entity.updatedAt
    )

    /**
     * Converts a domain [EmailRequest] into a persistable [EmailRequestJpaEntity].
     * When [EmailRequest.id] is null the entity will be treated as a new insert by JPA.
     */
    fun toEntity(domain: EmailRequest): EmailRequestJpaEntity = EmailRequestJpaEntity(
        id             = domain.id,
        publicId       = domain.publicId.value,
        userId         = domain.userId,
        recipientEmail = domain.recipientEmail,
        subject        = domain.subject,
        body           = domain.body,
        eventType      = domain.eventType,
        status         = domain.status,
        createdAt      = domain.createdAt,
        updatedAt      = domain.updatedAt
    )
}

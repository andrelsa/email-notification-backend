package com.emailnotification.adapter.out.persistence

import com.emailnotification.domain.model.EmailStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

/**
 * Spring Data JPA repository for [EmailRequestJpaEntity].
 *
 * Used exclusively by [EmailRequestPersistenceAdapter].
 */
interface EmailRequestJpaRepository : JpaRepository<EmailRequestJpaEntity, Long> {

    /** Looks up an email request by the externally visible UUID. */
    fun findByPublicId(publicId: UUID): Optional<EmailRequestJpaEntity>

    /** Returns all requests with the given [status]. */
    fun findAllByStatus(status: EmailStatus): List<EmailRequestJpaEntity>
}

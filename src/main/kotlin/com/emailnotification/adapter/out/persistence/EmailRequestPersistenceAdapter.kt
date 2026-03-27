package com.emailnotification.adapter.out.persistence

import com.emailnotification.domain.model.EmailRequest
import com.emailnotification.domain.model.EmailRequestId
import com.emailnotification.domain.model.EmailStatus
import com.emailnotification.domain.model.EmailStatusEntry
import com.emailnotification.domain.port.EmailRequestRepository
import org.springframework.stereotype.Component

/**
 * Output adapter — implements [EmailRequestRepository] using Spring Data JPA.
 *
 * Dependency direction: adapter -> domain port (never the other way around).
 */
@Component
class EmailRequestPersistenceAdapter(
    private val emailRequestJpaRepository: EmailRequestJpaRepository,
    private val emailStatusEntryJpaRepository: EmailStatusEntryJpaRepository,
    private val emailRequestMapper: EmailRequestMapper,
    private val emailStatusEntryMapper: EmailStatusEntryMapper
) : EmailRequestRepository {

    override fun save(emailRequest: EmailRequest): EmailRequest {
        val entity = emailRequestMapper.toEntity(emailRequest)
        return emailRequestMapper.toDomain(emailRequestJpaRepository.save(entity))
    }

    override fun findById(id: Long): EmailRequest? =
        emailRequestJpaRepository.findById(id)
            .map(emailRequestMapper::toDomain)
            .orElse(null)

    override fun findByPublicId(publicId: EmailRequestId): EmailRequest? =
        emailRequestJpaRepository.findByPublicId(publicId.value)
            .map(emailRequestMapper::toDomain)
            .orElse(null)

    override fun findAllByStatus(status: EmailStatus): List<EmailRequest> =
        emailRequestJpaRepository.findAllByStatus(status)
            .map(emailRequestMapper::toDomain)

    override fun saveStatusEntry(entry: EmailStatusEntry): EmailStatusEntry {
        val entity = emailStatusEntryMapper.toEntity(entry)
        return emailStatusEntryMapper.toDomain(emailStatusEntryJpaRepository.save(entity))
    }
}

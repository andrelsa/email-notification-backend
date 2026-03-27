package com.emailnotification.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for [EmailStatusEntryJpaEntity].
 *
 * Used exclusively by [EmailRequestPersistenceAdapter].
 * Only [save] is required — audit entries are never updated or deleted.
 */
interface EmailStatusEntryJpaRepository : JpaRepository<EmailStatusEntryJpaEntity, Long>

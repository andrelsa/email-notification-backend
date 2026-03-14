package com.emailnotification.adapter.out.persistence

import com.emailnotification.domain.model.UserStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

/**
 * Spring Data JPA repository for [UserJpaEntity].
 *
 * Used exclusively by [UserPersistenceAdapter].
 * The application layer must never inject this interface directly — it depends only on
 * [com.emailnotification.domain.port.UserRepository].
 */
interface UserJpaRepository : JpaRepository<UserJpaEntity, Long> {

    /** Looks up a user by the externally visible UUID. */
    fun findByPublicId(publicId: UUID): Optional<UserJpaEntity>

    /** Returns all users whose status differs from [status] (used to exclude DELETED). */
    fun findAllByStatusNot(status: UserStatus): List<UserJpaEntity>

    /** Returns true when [email] is already registered (any status). */
    fun existsByEmail(email: String): Boolean
}


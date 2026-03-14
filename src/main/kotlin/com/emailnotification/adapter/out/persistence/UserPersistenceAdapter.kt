package com.emailnotification.adapter.out.persistence

import com.emailnotification.domain.model.User
import com.emailnotification.domain.model.UserId
import com.emailnotification.domain.model.UserStatus
import com.emailnotification.domain.port.UserRepository
import org.springframework.stereotype.Component

/**
 * Output adapter — implements [UserRepository] using Spring Data JPA.
 *
 * The application layer depends only on the domain port [UserRepository].
 * This class wires the contract to the JPA infrastructure.
 *
 * Dependency direction: adapter → domain port (never the other way around).
 */
@Component
class UserPersistenceAdapter(
    private val jpaRepository: UserJpaRepository,
    private val mapper: UserMapper
) : UserRepository {

    override fun save(user: User): User {
        val entity = mapper.toEntity(user)
        return mapper.toDomain(jpaRepository.save(entity))
    }

    override fun findByPublicId(publicId: UserId): User? =
        jpaRepository.findByPublicId(publicId.value)
            .map(mapper::toDomain)
            .orElse(null)

    /** Returns all users excluding those with status [UserStatus.DELETED]. */
    override fun findAll(): List<User> =
        jpaRepository.findAllByStatusNot(UserStatus.DELETED)
            .map(mapper::toDomain)

    override fun existsByEmail(email: String): Boolean =
        jpaRepository.existsByEmail(email)
}


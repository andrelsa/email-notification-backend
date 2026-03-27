package com.emailnotification.adapter.out.persistence

import com.emailnotification.domain.model.User
import com.emailnotification.domain.model.UserId
import org.springframework.stereotype.Component

/**
 * Maps between [UserJpaEntity] (persistence layer) and [User] (domain layer).
 *
 * Keeps both models fully decoupled: the domain knows nothing about JPA
 * and the JPA entity knows nothing about domain behaviour.
 */
@Component
class UserMapper {

    /**
     * Converts a JPA entity row into a pure domain [User].
     */
    fun toDomain(entity: UserJpaEntity): User = User(
        id = entity.id,
        publicId = UserId.from(entity.publicId),
        name = entity.name,
        email = entity.email,
        status = entity.status,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    /**
     * Converts a domain [User] into a persistable [UserJpaEntity].
     * When [User.id] is null the entity will be treated as a new insert by JPA.
     */
    fun toEntity(domain: User): UserJpaEntity = UserJpaEntity(
        id = domain.id,
        publicId = domain.publicId.value,
        name = domain.name,
        email = domain.email,
        status = domain.status,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt
    )
}


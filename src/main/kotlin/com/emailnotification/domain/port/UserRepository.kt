package com.emailnotification.domain.port

import com.emailnotification.domain.model.User
import com.emailnotification.domain.model.UserId

/**
 * Repository port (output port) for [User] persistence.
 *
 * Defined in the domain layer — no persistence framework dependency.
 * Implemented by the persistence adapter in the infrastructure layer.
 */
interface UserRepository {

    /** Persists a new user or updates an existing one. Returns the saved entity. */
    fun save(user: User): User

    /** Finds a user by its public UUID. Returns null if not found. */
    fun findByPublicId(publicId: UserId): User?

    /** Returns all non-deleted users. */
    fun findAll(): List<User>

    /** Returns true if an email address is already registered (regardless of status). */
    fun existsByEmail(email: String): Boolean
}


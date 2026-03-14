package com.emailnotification.domain.model

import java.time.LocalDateTime

/**
 * User domain entity.
 *
 * Pure domain class — no Spring/JPA annotations.
 * Business behaviour (state transitions) lives here.
 *
 * [id] is the internal database surrogate key (null before first persistence).
 * [publicId] is the externally visible identifier exposed through the API.
 */
data class User(
    val id: Long? = null,
    val publicId: UserId = UserId.generate(),
    val name: String,
    val email: String,
    val status: UserStatus = UserStatus.ACTIVE,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {

    /**
     * Returns a new [User] with status set to [UserStatus.INACTIVE].
     * Only an ACTIVE user can be deactivated.
     */
    fun deactivate(): User {
        require(status == UserStatus.ACTIVE) {
            "Only ACTIVE users can be deactivated. Current status: $status"
        }
        return copy(status = UserStatus.INACTIVE, updatedAt = LocalDateTime.now())
    }

    /**
     * Returns a new [User] with status set to [UserStatus.DELETED].
     * A DELETED user cannot be deleted again.
     */
    fun delete(): User {
        require(status != UserStatus.DELETED) {
            "User is already deleted."
        }
        return copy(status = UserStatus.DELETED, updatedAt = LocalDateTime.now())
    }

    /**
     * Returns true when the user is considered active.
     */
    fun isActive(): Boolean = status == UserStatus.ACTIVE
}


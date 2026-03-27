package com.emailnotification.adapter.`in`.web.dto

import com.emailnotification.domain.model.User
import java.time.LocalDateTime
/**
 * Response payload for all User endpoints.
 *
 * Exposes only [publicId] (UUID string) — the internal [User.id] (BIGINT) is never sent.
 */
data class UserResponse(
    val publicId: String,
    val name: String,
    val email: String,
    val status: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(
            publicId = user.publicId.toString(),
            name = user.name,
            email = user.email,
            status = user.status.name,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }
}

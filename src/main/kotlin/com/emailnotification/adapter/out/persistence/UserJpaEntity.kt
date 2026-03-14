package com.emailnotification.adapter.out.persistence

import com.emailnotification.domain.model.UserStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * JPA entity mapped to the [users] table.
 *
 * Lives exclusively in the persistence adapter — the domain layer never sees this class.
 * Converted to/from [com.emailnotification.domain.model.User] by [UserMapper].
 */
@Entity
@Table(name = "users")
class UserJpaEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /** Externally visible UUID — never updated after creation. */
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "email", nullable = false, unique = true)
    val email: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: UserStatus,

    /** Set once on creation — never updated (updatable = false). */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime
)


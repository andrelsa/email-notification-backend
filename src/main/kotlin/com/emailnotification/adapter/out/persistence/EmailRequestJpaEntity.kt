package com.emailnotification.adapter.out.persistence

import com.emailnotification.domain.model.EmailEventType
import com.emailnotification.domain.model.EmailStatus
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
 * JPA entity mapped to the [email_request] table.
 *
 * Lives exclusively in the persistence adapter — the domain layer never sees this class.
 * Converted to/from [com.emailnotification.domain.model.EmailRequest] by [EmailRequestMapper].
 */
@Entity
@Table(name = "email_request")
class EmailRequestJpaEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /** Externally visible UUID — never updated after creation. */
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID,

    /** Internal FK to users.id — never updated after creation. */
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(name = "recipient_email", nullable = false, updatable = false)
    val recipientEmail: String,

    @Column(name = "subject", nullable = false, updatable = false)
    val subject: String,

    @Column(name = "body", nullable = false, updatable = false)
    val body: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    val eventType: EmailEventType,

    /** Mutable — updated on every status transition. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: EmailStatus,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime
)

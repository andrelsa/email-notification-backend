package com.emailnotification.adapter.out.persistence

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

/**
 * JPA entity mapped to the [email_status] table.
 *
 * Immutable audit trail — records are only inserted, never updated.
 * Converted to/from [com.emailnotification.domain.model.EmailStatusEntry] by [EmailStatusEntryMapper].
 */
@Entity
@Table(name = "email_status")
class EmailStatusEntryJpaEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /** FK to email_request.id — set once, never updated. */
    @Column(name = "email_request_id", nullable = false, updatable = false)
    val emailRequestId: Long,

    /** Null for the initial PENDING entry (no prior status exists). */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", updatable = false)
    val previousStatus: EmailStatus?,

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, updatable = false)
    val newStatus: EmailStatus,

    @Column(name = "description", updatable = false)
    val description: String?,

    @Column(name = "occurred_at", nullable = false, updatable = false)
    val occurredAt: LocalDateTime
)

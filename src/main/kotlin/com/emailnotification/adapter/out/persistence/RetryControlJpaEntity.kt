package com.emailnotification.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * JPA entity mapped to the [retry_control] table.
 *
 * One record per [EmailRequestJpaEntity] that has entered the retry flow (1:1 via UNIQUE FK).
 * Converted to/from [com.emailnotification.domain.model.RetryControl] by [RetryControlMapper].
 */
@Entity
@Table(name = "retry_control")
class RetryControlJpaEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /** FK to email_request.id (UNIQUE — 1:1 relationship). Never updated after creation. */
    @Column(name = "email_request_id", nullable = false, unique = true, updatable = false)
    val emailRequestId: Long,

    @Column(name = "attempt_count", nullable = false)
    val attemptCount: Int,

    @Column(name = "max_attempts", nullable = false)
    val maxAttempts: Int,

    @Column(name = "last_attempt_at")
    val lastAttemptAt: LocalDateTime?,

    @Column(name = "next_attempt_at")
    val nextAttemptAt: LocalDateTime?,

    @Column(name = "last_error_message")
    val lastErrorMessage: String?,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime
)

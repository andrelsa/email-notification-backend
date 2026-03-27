package com.emailnotification.domain.model

import com.emailnotification.domain.exception.InvalidEmailStatusTransitionException
import java.time.LocalDateTime

/**
 * Domain entity representing a request to send an email notification.
 *
 * Pure domain class — no Spring/JPA annotations.
 * Business behaviour (state transitions) lives here.
 *
 * [id] is the internal database surrogate key (null before first persistence).
 * [publicId] is the externally visible identifier exposed through the API.
 * [userId] references the internal PK of the [User] that triggered the event.
 *
 * The email body and subject are resolved from templates before this entity
 * is created; by the time it is persisted, the content is already finalised.
 */
data class EmailRequest(
    val id: Long? = null,
    val publicId: EmailRequestId = EmailRequestId.generate(),
    val userId: Long,
    val recipientEmail: String,
    val subject: String,
    val body: String,
    val eventType: EmailEventType,
    val status: EmailStatus = EmailStatus.PENDING,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {

    /**
     * Transitions this request to [EmailStatus.SENT].
     * Only allowed from [EmailStatus.PENDING] or [EmailStatus.RETRYING].
     *
     * @throws InvalidEmailStatusTransitionException if the current status does not allow this transition.
     */
    fun markAsSent(): EmailRequest {
        if (!status.canTransitionTo(EmailStatus.SENT)) {
            throw InvalidEmailStatusTransitionException(status, EmailStatus.SENT)
        }
        return copy(status = EmailStatus.SENT, updatedAt = LocalDateTime.now())
    }

    /**
     * Transitions this request to [EmailStatus.RETRYING].
     * Only allowed from [EmailStatus.PENDING] or [EmailStatus.RETRYING].
     *
     * @throws InvalidEmailStatusTransitionException if the current status does not allow this transition.
     */
    fun markAsRetrying(): EmailRequest {
        if (!status.canTransitionTo(EmailStatus.RETRYING)) {
            throw InvalidEmailStatusTransitionException(status, EmailStatus.RETRYING)
        }
        return copy(status = EmailStatus.RETRYING, updatedAt = LocalDateTime.now())
    }

    /**
     * Transitions this request to [EmailStatus.FAILED].
     * Only allowed from [EmailStatus.PENDING] or [EmailStatus.RETRYING].
     *
     * @throws InvalidEmailStatusTransitionException if the current status does not allow this transition.
     */
    fun markAsFailed(): EmailRequest {
        if (!status.canTransitionTo(EmailStatus.FAILED)) {
            throw InvalidEmailStatusTransitionException(status, EmailStatus.FAILED)
        }
        return copy(status = EmailStatus.FAILED, updatedAt = LocalDateTime.now())
    }

    /** Returns true when the email has not yet been delivered or permanently failed. */
    fun isPending(): Boolean = status == EmailStatus.PENDING

    /** Returns true when the email is awaiting a retry attempt. */
    fun isRetrying(): Boolean = status == EmailStatus.RETRYING

    /** Returns true when the email has been successfully delivered. */
    fun isSent(): Boolean = status == EmailStatus.SENT

    /** Returns true when all retry attempts are exhausted. */
    fun isFailed(): Boolean = status == EmailStatus.FAILED
}

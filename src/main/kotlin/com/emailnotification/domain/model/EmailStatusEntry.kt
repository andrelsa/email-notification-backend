package com.emailnotification.domain.model

import java.time.LocalDateTime

/**
 * Immutable audit trail record for a single [EmailStatus] transition of an [EmailRequest].
 *
 * A new [EmailStatusEntry] is created every time the status of an [EmailRequest] changes,
 * providing a full history of the request lifecycle.
 *
 * Maps to the `email_status` table.
 *
 * [id] is null before persistence.
 * [emailRequestId] references the internal PK of the parent [EmailRequest].
 * [previousStatus] is null for the initial PENDING entry (no prior status).
 */
data class EmailStatusEntry(
    val id: Long? = null,
    val emailRequestId: Long,
    val previousStatus: EmailStatus?,
    val newStatus: EmailStatus,
    val description: String? = null,
    val occurredAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        /**
         * Factory for the initial status entry when an [EmailRequest] is created.
         * Previous status is null because there is no prior state.
         */
        fun initial(emailRequestId: Long, description: String? = null): EmailStatusEntry =
            EmailStatusEntry(
                emailRequestId = emailRequestId,
                previousStatus = null,
                newStatus = EmailStatus.PENDING,
                description = description
            )

        /**
         * Factory for a status transition entry.
         */
        fun transition(
            emailRequestId: Long,
            from: EmailStatus,
            to: EmailStatus,
            description: String? = null
        ): EmailStatusEntry =
            EmailStatusEntry(
                emailRequestId = emailRequestId,
                previousStatus = from,
                newStatus = to,
                description = description
            )
    }
}

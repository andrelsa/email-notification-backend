package com.emailnotification.domain.port

import com.emailnotification.domain.model.EmailRequest
import com.emailnotification.domain.model.EmailRequestId
import com.emailnotification.domain.model.EmailStatus
import com.emailnotification.domain.model.EmailStatusEntry

/**
 * Repository port (output port) for [EmailRequest] and [EmailStatusEntry] persistence.
 *
 * Defined in the domain layer — no persistence framework dependency.
 * Implemented by the persistence adapter in the infrastructure layer.
 */
interface EmailRequestRepository {

    /** Persists a new request or updates an existing one. Returns the saved entity. */
    fun save(emailRequest: EmailRequest): EmailRequest

    /** Finds a request by its internal surrogate key. Returns null if not found. */
    fun findById(id: Long): EmailRequest?

    /** Finds a request by its public UUID. Returns null if not found. */
    fun findByPublicId(publicId: EmailRequestId): EmailRequest?

    /**
     * Returns all requests that match the given [status].
     *
     * This is a generic status-based query method that can be used by
     * application services and operational/reconciliation flows.
     */
    fun findAllByStatus(status: EmailStatus): List<EmailRequest>

    /**
     * Persists a status transition audit entry.
     *
     * Called every time an [EmailRequest] transitions between statuses to maintain
     * a full history in the `email_status` table.
     */
    fun saveStatusEntry(entry: EmailStatusEntry): EmailStatusEntry
}

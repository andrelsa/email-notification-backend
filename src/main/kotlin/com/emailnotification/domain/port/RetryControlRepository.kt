package com.emailnotification.domain.port

import com.emailnotification.domain.model.RetryControl
import java.time.LocalDateTime

/**
 * Repository port (output port) for [RetryControl] persistence.
 *
 * Defined in the domain layer — no persistence framework dependency.
 * Implemented by the persistence adapter in the infrastructure layer.
 */
interface RetryControlRepository {

    /** Persists a new retry control record or updates an existing one. Returns the saved entity. */
    fun save(retryControl: RetryControl): RetryControl

    /**
     * Finds the retry control record linked to the given [emailRequestId] (internal PK).
     * Returns null if no retry has been scheduled for that request.
     */
    fun findByEmailRequestId(emailRequestId: Long): RetryControl?

    /**
     * Returns all retry control records whose [RetryControl.nextAttemptAt] is on or before
     * [now] and whose linked [com.emailnotification.domain.model.EmailRequest] is still
     * in a non-terminal status (RETRYING).
     *
     * Used by the scheduled retry job to find requests that are overdue for a retry attempt.
     */
    fun findAllReadyForRetry(now: LocalDateTime): List<RetryControl>
}

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
     * [now], whose linked [com.emailnotification.domain.model.EmailRequest] is still
     * in a non-terminal status (RETRYING), **and** whose [RetryControl.lockedUntil] is either
     * null or has already expired.
     *
     * Used by the scheduled retry job to find requests that are overdue for a retry attempt.
     * Already-claimed rows are excluded so the scheduler does not hand them to
     * [com.emailnotification.application.usecase.ProcessRetryUseCase] unnecessarily.
     */
    fun findAllReadyForRetry(now: LocalDateTime): List<RetryControl>

    /**
     * Atomically claims the retry record identified by [id] for exclusive processing.
     *
     * Sets [RetryControl.lockedUntil] to [lockExpiry] only when the row is currently
     * unclaimed (i.e. `locked_until IS NULL OR locked_until < NOW()`).
     * The underlying UPDATE uses `FOR UPDATE SKIP LOCKED` so competing instances return
     * immediately rather than blocking.
     *
     * @param id         the [RetryControl.id] of the record to claim.
     * @param lockExpiry absolute timestamp after which the lock is considered expired.
     *                   Should be at least as long as the maximum expected processing time.
     * @return `true` if this call won the claim (rows-affected == 1);
     *         `false` if another instance already holds the lock.
     */
    fun tryClaimForProcessing(id: Long, lockExpiry: LocalDateTime): Boolean
}

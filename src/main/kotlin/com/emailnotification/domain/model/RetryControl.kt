package com.emailnotification.domain.model

import java.time.LocalDateTime

/**
 * Domain entity tracking retry state for a single [EmailRequest].
 *
 * One [RetryControl] record exists per [EmailRequest] that has entered the retry flow.
 * It tracks the number of attempts made, the maximum allowed, timing information,
 * and the last error message for diagnostics.
 *
 * Maps to the `retry_control` table.
 *
 * [id] is null before persistence.
 * [emailRequestId] references the internal PK of the parent [EmailRequest].
 */
data class RetryControl(
    val id: Long? = null,
    val emailRequestId: Long,
    val attemptCount: Int = 0,
    val maxAttempts: Int = 3,
    val lastAttemptAt: LocalDateTime? = null,
    val nextAttemptAt: LocalDateTime? = null,
    val lastErrorMessage: String? = null,
    /**
     * When non-null and in the future, this record is claimed by a scheduler instance
     * and must not be processed by any other instance.
     * Set atomically via [RetryControlRepository.tryClaimForProcessing]; expires naturally
     * so a crashed instance never permanently strands the row.
     */
    val lockedUntil: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {

    /**
     * Returns true if the number of attempts has reached or exceeded [maxAttempts].
     * When true, the next failed attempt should transition the request to [EmailStatus.FAILED].
     */
    fun hasExceededMaxAttempts(): Boolean = attemptCount >= maxAttempts

    /**
     * Returns true if there is a scheduled retry and the current time is on or past
     * the [nextAttemptAt] timestamp.
     */
    fun isReadyToRetry(now: LocalDateTime = LocalDateTime.now()): Boolean =
        nextAttemptAt != null && !now.isBefore(nextAttemptAt)

    /**
     * Returns a new [RetryControl] recording a new failed attempt.
     *
     * @param errorMessage the error message from the failed send attempt.
     * @param nextAttemptAt when the next retry should be scheduled.
     */
    fun recordFailedAttempt(
        errorMessage: String,
        nextAttemptAt: LocalDateTime
    ): RetryControl = copy(
        attemptCount = attemptCount + 1,
        lastAttemptAt = LocalDateTime.now(),
        nextAttemptAt = nextAttemptAt,
        lastErrorMessage = errorMessage,
        updatedAt = LocalDateTime.now()
    )

    /**
     * Returns the remaining number of allowed attempts.
     * Returns 0 if max attempts has been exceeded.
     */
    fun remainingAttempts(): Int = maxOf(0, maxAttempts - attemptCount)
}

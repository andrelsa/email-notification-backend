package com.emailnotification.domain.model

/**
 * Lifecycle status of an [EmailRequest].
 *
 * State machine:
 *   PENDING  -> SENT      (success on first attempt)
 *   PENDING  -> RETRYING  (first attempt failed, will retry)
 *   PENDING  -> FAILED    (first attempt failed, max_attempts = 1)
 *   RETRYING -> SENT      (success on a retry attempt)
 *   RETRYING -> RETRYING  (retry attempt failed, still under max attempts)
 *   RETRYING -> FAILED    (retry attempt failed, max attempts exceeded)
 *
 * [SENT] and [FAILED] are terminal states — no further transitions allowed.
 * Maps to the `status` column in the `email_request` table.
 */
enum class EmailStatus {
    /** Initial state. The email has been requested but not yet sent. */
    PENDING,

    /** A send attempt failed and the request is scheduled for retry. */
    RETRYING,

    /** The email was successfully delivered. Terminal state. */
    SENT,

    /** All retry attempts exhausted without success. Terminal state. */
    FAILED;

    /**
     * Returns true if a transition from this status to [target] is valid
     * according to the state machine rules.
     */
    fun canTransitionTo(target: EmailStatus): Boolean = when (this) {
        PENDING  -> target == SENT || target == RETRYING || target == FAILED
        RETRYING -> target == SENT || target == RETRYING || target == FAILED
        SENT     -> false
        FAILED   -> false
    }

    /** Returns true if this is a terminal state (no further transitions possible). */
    fun isTerminal(): Boolean = this == SENT || this == FAILED
}

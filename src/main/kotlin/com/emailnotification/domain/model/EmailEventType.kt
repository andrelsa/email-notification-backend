package com.emailnotification.domain.model

/**
 * Represents the type of domain event that triggered an email notification.
 *
 * Maps to the `event_type` column in the `email_request` table.
 * Each value corresponds to one of the domain events published during
 * user lifecycle transitions.
 */
enum class EmailEventType {
    /** Triggered by UserCreatedEvent. */
    USER_CREATED,

    /** Triggered by UserDeactivatedEvent. */
    USER_DEACTIVATED,

    /** Triggered by UserDeletedEvent. */
    USER_DELETED
}

package com.emailnotification.domain.model

import java.util.UUID

/**
 * Value object representing the public (external) identifier of an [EmailRequest].
 *
 * Wraps a [UUID] to provide type safety and prevent accidental mixing with other IDs.
 * The internal surrogate key (id: Long) is never exposed through the API.
 */
@JvmInline
value class EmailRequestId(val value: UUID) {
    companion object {
        fun generate(): EmailRequestId = EmailRequestId(UUID.randomUUID())
        fun from(value: String): EmailRequestId = EmailRequestId(UUID.fromString(value))
        fun from(value: UUID): EmailRequestId = EmailRequestId(value)
    }

    override fun toString(): String = value.toString()
}

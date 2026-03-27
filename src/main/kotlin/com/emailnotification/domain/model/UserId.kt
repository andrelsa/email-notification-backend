package com.emailnotification.domain.model

import java.util.UUID

@JvmInline
value class UserId(val value: UUID) {
    companion object {
        fun generate(): UserId = UserId(UUID.randomUUID())
        fun from(value: String): UserId = UserId(UUID.fromString(value))
        fun from(value: UUID): UserId = UserId(value)
    }

    override fun toString(): String = value.toString()
}


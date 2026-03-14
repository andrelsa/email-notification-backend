package com.emailnotification.domain.event

import com.emailnotification.domain.model.User

/**
 * Domain event published after a [User] is successfully created.
 * Pure data class — no framework dependency.
 */
data class UserCreatedEvent(val user: User)


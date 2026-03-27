package com.emailnotification.domain.event

import com.emailnotification.domain.model.User

/**
 * Domain event published after a [User] is successfully deactivated.
 * Pure data class — no framework dependency.
 */
data class UserDeactivatedEvent(val user: User)


package com.emailnotification.domain.event

import com.emailnotification.domain.model.User

/**
 * Domain event published after a [User] is successfully deleted.
 * Pure data class — no framework dependency.
 */
data class UserDeletedEvent(val user: User)


package com.emailnotification.domain.exception

import com.emailnotification.domain.model.UserId

/**
 * Thrown when a [com.emailnotification.domain.model.User] cannot be found by its public identifier.
 */
class UserNotFoundException(publicId: UserId) :
    RuntimeException("User not found: ${publicId.value}")


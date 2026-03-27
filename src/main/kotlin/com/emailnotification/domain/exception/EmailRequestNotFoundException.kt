package com.emailnotification.domain.exception

import java.util.UUID

/**
 * Thrown when an [com.emailnotification.domain.model.EmailRequest] cannot be found
 * by its public identifier.
 */
class EmailRequestNotFoundException(publicId: UUID) :
    RuntimeException("EmailRequest not found: $publicId")

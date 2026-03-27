package com.emailnotification.domain.exception

import com.emailnotification.domain.model.EmailStatus

/**
 * Thrown when an attempt is made to transition an
 * [com.emailnotification.domain.model.EmailRequest] between two incompatible statuses.
 *
 * @param from the current status of the request.
 * @param to   the target status that was requested.
 */
class InvalidEmailStatusTransitionException(from: EmailStatus, to: EmailStatus) :
    RuntimeException("Invalid EmailRequest status transition: $from -> $to")

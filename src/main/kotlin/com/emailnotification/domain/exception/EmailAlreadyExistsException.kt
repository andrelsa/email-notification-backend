package com.emailnotification.domain.exception

/**
 * Thrown when attempting to register an e-mail address that is already in use.
 */
class EmailAlreadyExistsException(email: String) :
    RuntimeException("E-mail already registered: $email")


package com.emailnotification.application.usecase

import com.emailnotification.domain.event.UserCreatedEvent
import com.emailnotification.domain.exception.EmailAlreadyExistsException
import com.emailnotification.domain.model.User
import com.emailnotification.domain.port.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Creates a new [User] and publishes a [UserCreatedEvent].
 *
 * Validation: e-mail must be unique across all users (regardless of status).
 */
@Service
@Transactional
class CreateUserUseCase(
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    fun execute(name: String, email: String): User {
        if (userRepository.existsByEmail(email)) {
            throw EmailAlreadyExistsException(email)
        }

        val user = User(name = name, email = email)
        val saved = userRepository.save(user)

        eventPublisher.publishEvent(UserCreatedEvent(saved))

        return saved
    }
}


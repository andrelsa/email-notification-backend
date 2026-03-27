package com.emailnotification.application.usecase

import com.emailnotification.domain.event.UserDeactivatedEvent
import com.emailnotification.domain.exception.UserNotFoundException
import com.emailnotification.domain.model.User
import com.emailnotification.domain.model.UserId
import com.emailnotification.domain.port.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Deactivates a [User] (ACTIVE → INACTIVE) and publishes a [UserDeactivatedEvent].
 *
 * @throws [UserNotFoundException] when no user exists with the given [publicId].
 * @throws [IllegalArgumentException] when the user is not ACTIVE (enforced by domain).
 */
@Service
@Transactional
class DeactivateUserUseCase(
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    fun execute(publicId: UserId): User {
        val user = userRepository.findByPublicId(publicId)
            ?: throw UserNotFoundException(publicId)

        val deactivated = user.deactivate()
        val saved = userRepository.save(deactivated)

        eventPublisher.publishEvent(UserDeactivatedEvent(saved))

        return saved
    }
}


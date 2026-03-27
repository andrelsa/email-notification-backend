package com.emailnotification.application.usecase

import com.emailnotification.domain.event.UserDeletedEvent
import com.emailnotification.domain.exception.UserNotFoundException
import com.emailnotification.domain.model.User
import com.emailnotification.domain.model.UserId
import com.emailnotification.domain.port.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Soft-deletes a [User] (ACTIVE/INACTIVE → DELETED) and publishes a [UserDeletedEvent].
 *
 * @throws [UserNotFoundException] when no user exists with the given [publicId].
 * @throws [IllegalArgumentException] when the user is already DELETED (enforced by domain).
 */
@Service
@Transactional
class DeleteUserUseCase(
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    fun execute(publicId: UserId): User {
        val user = userRepository.findByPublicId(publicId)
            ?: throw UserNotFoundException(publicId)

        val deleted = user.delete()
        val saved = userRepository.save(deleted)

        eventPublisher.publishEvent(UserDeletedEvent(saved))

        return saved
    }
}


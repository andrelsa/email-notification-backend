package com.emailnotification.application.usecase

import com.emailnotification.domain.model.User
import com.emailnotification.domain.model.UserId
import com.emailnotification.domain.port.UserRepository
import com.emailnotification.domain.exception.UserNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Returns a single [User] by its public identifier.
 *
 * @throws [UserNotFoundException] when no user exists with the given [publicId].
 */
@Service
@Transactional(readOnly = true)
class GetUserUseCase(
    private val userRepository: UserRepository
) {

    fun execute(publicId: UserId): User =
        userRepository.findByPublicId(publicId)
            ?: throw UserNotFoundException(publicId)
}


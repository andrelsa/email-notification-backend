package com.emailnotification.application.usecase

import com.emailnotification.domain.model.User
import com.emailnotification.domain.port.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Returns all non-deleted users.
 */
@Service
@Transactional(readOnly = true)
class ListUsersUseCase(
    private val userRepository: UserRepository
) {

    fun execute(): List<User> = userRepository.findAll()
}


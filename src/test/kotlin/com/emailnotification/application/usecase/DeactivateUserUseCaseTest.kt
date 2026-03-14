package com.emailnotification.application.usecase

import com.emailnotification.domain.event.UserDeactivatedEvent
import com.emailnotification.domain.exception.UserNotFoundException
import com.emailnotification.domain.model.User
import com.emailnotification.domain.model.UserId
import com.emailnotification.domain.model.UserStatus
import com.emailnotification.domain.port.UserRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher

class DeactivateUserUseCaseTest {

    private val userRepository: UserRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private lateinit var useCase: DeactivateUserUseCase

    @BeforeEach
    fun setUp() {
        useCase = DeactivateUserUseCase(userRepository, eventPublisher)
    }

    @Test
    fun `should deactivate ACTIVE user and publish UserDeactivatedEvent`() {
        // given
        val publicId = UserId.generate()
        val activeUser = User(id = 1L, publicId = publicId, name = "Alice", email = "alice@example.com")
        every { userRepository.findByPublicId(publicId) } returns activeUser
        every { userRepository.save(any()) } answers { firstArg() }
        justRun { eventPublisher.publishEvent(any<Any>()) }

        // when
        val result = useCase.execute(publicId)

        // then
        assertEquals(UserStatus.INACTIVE, result.status)
        verify(exactly = 1) { userRepository.save(match { it.status == UserStatus.INACTIVE }) }
        verify(exactly = 1) { eventPublisher.publishEvent(match<Any> { it is UserDeactivatedEvent }) }
    }

    @Test
    fun `should throw UserNotFoundException when user does not exist`() {
        // given
        val publicId = UserId.generate()
        every { userRepository.findByPublicId(publicId) } returns null

        // when / then
        assertThrows<UserNotFoundException> {
            useCase.execute(publicId)
        }
        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `should propagate IllegalArgumentException when user is not ACTIVE`() {
        // given — domain rule: only ACTIVE users can be deactivated
        val publicId = UserId.generate()
        val inactiveUser = User(id = 1L, publicId = publicId, name = "Alice", email = "alice@example.com",
            status = UserStatus.INACTIVE)
        every { userRepository.findByPublicId(publicId) } returns inactiveUser

        // when / then
        assertThrows<IllegalArgumentException> {
            useCase.execute(publicId)
        }
        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }
}


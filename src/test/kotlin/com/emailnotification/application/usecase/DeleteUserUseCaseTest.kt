package com.emailnotification.application.usecase

import com.emailnotification.domain.event.UserDeletedEvent
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

class DeleteUserUseCaseTest {

    private val userRepository: UserRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private lateinit var useCase: DeleteUserUseCase

    @BeforeEach
    fun setUp() {
        useCase = DeleteUserUseCase(userRepository, eventPublisher)
    }

    @Test
    fun `should delete ACTIVE user and publish UserDeletedEvent`() {
        // given
        val publicId = UserId.generate()
        val activeUser = User(id = 1L, publicId = publicId, name = "Bob", email = "bob@example.com")
        every { userRepository.findByPublicId(publicId) } returns activeUser
        every { userRepository.save(any()) } answers { firstArg() }
        justRun { eventPublisher.publishEvent(any<Any>()) }

        // when
        val result = useCase.execute(publicId)

        // then
        assertEquals(UserStatus.DELETED, result.status)
        verify(exactly = 1) { userRepository.save(match { it.status == UserStatus.DELETED }) }
        verify(exactly = 1) { eventPublisher.publishEvent(match<Any> { it is UserDeletedEvent }) }
    }

    @Test
    fun `should delete INACTIVE user and publish UserDeletedEvent`() {
        // given
        val publicId = UserId.generate()
        val inactiveUser = User(id = 1L, publicId = publicId, name = "Bob", email = "bob@example.com",
            status = UserStatus.INACTIVE)
        every { userRepository.findByPublicId(publicId) } returns inactiveUser
        every { userRepository.save(any()) } answers { firstArg() }
        justRun { eventPublisher.publishEvent(any<Any>()) }

        // when
        val result = useCase.execute(publicId)

        // then
        assertEquals(UserStatus.DELETED, result.status)
        verify(exactly = 1) { eventPublisher.publishEvent(match<Any> { it is UserDeletedEvent }) }
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
    fun `should propagate IllegalArgumentException when user is already DELETED`() {
        // given — domain rule: already-deleted user cannot be deleted again
        val publicId = UserId.generate()
        val deletedUser = User(id = 1L, publicId = publicId, name = "Bob", email = "bob@example.com",
            status = UserStatus.DELETED)
        every { userRepository.findByPublicId(publicId) } returns deletedUser

        // when / then
        assertThrows<IllegalArgumentException> {
            useCase.execute(publicId)
        }
        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }
}


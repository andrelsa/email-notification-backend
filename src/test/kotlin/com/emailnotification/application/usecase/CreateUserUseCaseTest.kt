package com.emailnotification.application.usecase

import com.emailnotification.domain.event.UserCreatedEvent
import com.emailnotification.domain.exception.EmailAlreadyExistsException
import com.emailnotification.domain.model.User
import com.emailnotification.domain.model.UserStatus
import com.emailnotification.domain.port.UserRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher

class CreateUserUseCaseTest {

    private val userRepository: UserRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()

    private lateinit var useCase: CreateUserUseCase

    @BeforeEach
    fun setUp() {
        useCase = CreateUserUseCase(userRepository, eventPublisher)
    }

    @Test
    fun `should create user and publish UserCreatedEvent when email is not registered`() {
        // given
        every { userRepository.existsByEmail("john@example.com") } returns false
        every { userRepository.save(any()) } answers { firstArg() }
        justRun { eventPublisher.publishEvent(any<Any>()) }

        // when
        val result = useCase.execute("John Doe", "john@example.com")

        // then
        assertEquals("John Doe", result.name)
        assertEquals("john@example.com", result.email)
        assertEquals(UserStatus.ACTIVE, result.status)

        verify(exactly = 1) { userRepository.save(any()) }
        verify(exactly = 1) { eventPublisher.publishEvent(match<Any> { it is UserCreatedEvent }) }
    }

    @Test
    fun `should throw EmailAlreadyExistsException and not save when email is already registered`() {
        // given
        every { userRepository.existsByEmail("john@example.com") } returns true

        // when / then
        assertThrows<EmailAlreadyExistsException> {
            useCase.execute("John Doe", "john@example.com")
        }
        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }
}


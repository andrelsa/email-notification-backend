package com.emailnotification.application.usecase

import com.emailnotification.domain.exception.UserNotFoundException
import com.emailnotification.domain.model.User
import com.emailnotification.domain.model.UserId
import com.emailnotification.domain.model.UserStatus
import com.emailnotification.domain.port.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GetUserUseCaseTest {

    private val userRepository: UserRepository = mockk()
    private lateinit var useCase: GetUserUseCase

    @BeforeEach
    fun setUp() {
        useCase = GetUserUseCase(userRepository)
    }

    @Test
    fun `should return user when found by publicId`() {
        // given
        val publicId = UserId.generate()
        val user = User(id = 1L, publicId = publicId, name = "Alice", email = "alice@example.com")
        every { userRepository.findByPublicId(publicId) } returns user

        // when
        val result = useCase.execute(publicId)

        // then
        assertEquals(publicId, result.publicId)
        assertEquals("Alice", result.name)
        assertEquals(UserStatus.ACTIVE, result.status)
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
    }
}


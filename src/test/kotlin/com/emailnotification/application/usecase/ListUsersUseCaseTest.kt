package com.emailnotification.application.usecase

import com.emailnotification.domain.model.User
import com.emailnotification.domain.model.UserStatus
import com.emailnotification.domain.port.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ListUsersUseCaseTest {

    private val userRepository: UserRepository = mockk()
    private lateinit var useCase: ListUsersUseCase

    @BeforeEach
    fun setUp() {
        useCase = ListUsersUseCase(userRepository)
    }

    @Test
    fun `should return all non-deleted users`() {
        // given
        val users = listOf(
            User(id = 1L, name = "Alice", email = "alice@example.com"),
            User(id = 2L, name = "Bob", email = "bob@example.com", status = UserStatus.INACTIVE)
        )
        every { userRepository.findAll() } returns users

        // when
        val result = useCase.execute()

        // then
        assertEquals(2, result.size)
        assertEquals("Alice", result[0].name)
        assertEquals("Bob", result[1].name)
    }

    @Test
    fun `should return empty list when no users exist`() {
        // given
        every { userRepository.findAll() } returns emptyList()

        // when
        val result = useCase.execute()

        // then
        assertTrue(result.isEmpty())
    }
}


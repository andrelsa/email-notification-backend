package com.emailnotification.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UserTest {

    private fun activeUser() = User(
        id = 1L,
        name = "John Doe",
        email = "john@example.com",
        status = UserStatus.ACTIVE
    )

    // ── deactivate ────────────────────────────────────────────────────────────

    @Test
    fun `deactivate should transition ACTIVE user to INACTIVE`() {
        val user = activeUser()
        val result = user.deactivate()
        assertEquals(UserStatus.INACTIVE, result.status)
        assertTrue(result.updatedAt >= user.updatedAt)
    }

    @Test
    fun `deactivate should throw when user is already INACTIVE`() {
        val user = activeUser().copy(status = UserStatus.INACTIVE)
        val ex = assertThrows<IllegalArgumentException> { user.deactivate() }
        assertTrue(ex.message!!.contains("INACTIVE"))
    }

    @Test
    fun `deactivate should throw when user is DELETED`() {
        val user = activeUser().copy(status = UserStatus.DELETED)
        val ex = assertThrows<IllegalArgumentException> { user.deactivate() }
        assertTrue(ex.message!!.contains("DELETED"))
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun `delete should transition ACTIVE user to DELETED`() {
        val result = activeUser().delete()
        assertEquals(UserStatus.DELETED, result.status)
    }

    @Test
    fun `delete should transition INACTIVE user to DELETED`() {
        val user = activeUser().copy(status = UserStatus.INACTIVE)
        val result = user.delete()
        assertEquals(UserStatus.DELETED, result.status)
    }

    @Test
    fun `delete should throw when user is already DELETED`() {
        val user = activeUser().copy(status = UserStatus.DELETED)
        val ex = assertThrows<IllegalArgumentException> { user.delete() }
        assertTrue(ex.message!!.contains("already deleted"))
    }

    // ── isActive ──────────────────────────────────────────────────────────────

    @Test
    fun `isActive should return true for ACTIVE user`() {
        assertTrue(activeUser().isActive())
    }

    @Test
    fun `isActive should return false for INACTIVE user`() {
        assertFalse(activeUser().copy(status = UserStatus.INACTIVE).isActive())
    }

    @Test
    fun `isActive should return false for DELETED user`() {
        assertFalse(activeUser().copy(status = UserStatus.DELETED).isActive())
    }
}


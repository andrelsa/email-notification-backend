package com.emailnotification.adapter.`in`.web

import com.emailnotification.adapter.`in`.web.dto.CreateUserRequest
import com.emailnotification.adapter.out.persistence.UserJpaRepository
import com.emailnotification.application.usecase.SendEmailUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Integration tests for [UserController].
 *
 * Loads a full Spring application context with [MockMvc] against a real PostgreSQL
 * database. The database is started automatically by Testcontainers using the
 * `jdbc:tc:postgresql:16-alpine:///` JDBC URL configured in `application-test.yml`.
 * No running PostgreSQL instance is required — Docker must be available on the host.
 *
 * Each test starts with a clean `users` table (truncated in [cleanDatabase]).
 *
 * [SendEmailUseCase] is mocked to prevent the async [com.emailnotification.adapter.`in`.event.UserEventListener]
 * from persisting email_request rows that would cause FK constraint violations when
 * [cleanDatabase] deletes users between tests.
 * The full email notification flow is covered by EmailFlowIntegrationTest (T3.10).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    // Mocked to isolate user CRUD tests from async email side-effects (see KDoc above)
    @MockitoBean private lateinit var sendEmailUseCase: SendEmailUseCase

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userJpaRepository: UserJpaRepository

    @BeforeEach
    fun cleanDatabase() {
        userJpaRepository.deleteAll()
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun body(name: String = "Alice", email: String = "alice@example.com"): String =
        objectMapper.writeValueAsString(CreateUserRequest(name, email))

    /** POSTs a user and returns its publicId for use in subsequent assertions. */
    private fun createUser(name: String = "Alice", email: String = "alice@example.com"): String {
        val result = mockMvc.perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(name, email))
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString)
            .get("publicId").asText()
    }

    // ── POST /users ────────────────────────────────────────────────────────────

    @Test
    fun `POST users - should return 201 with ACTIVE user`() {
        mockMvc.perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body())
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Alice"))
            .andExpect(jsonPath("$.email").value("alice@example.com"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.publicId").isNotEmpty)
    }

    @Test
    fun `POST users - should return 400 when name is blank`() {
        mockMvc.perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(name = ""))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").isNotEmpty)
    }

    @Test
    fun `POST users - should return 400 when email format is invalid`() {
        mockMvc.perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(email = "not-an-email"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Bad Request"))
    }

    @Test
    fun `POST users - should return 409 when email is already registered`() {
        createUser(email = "alice@example.com")

        mockMvc.perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(name = "Alice 2", email = "alice@example.com"))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("Conflict"))
    }

    // ── GET /users ─────────────────────────────────────────────────────────────

    @Test
    fun `GET users - should return empty list when no users exist`() {
        mockMvc.perform(get("/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `GET users - should return all non-deleted users`() {
        createUser(name = "Alice", email = "alice@example.com")
        createUser(name = "Bob",   email = "bob@example.com")

        mockMvc.perform(get("/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `GET users - should not include soft-deleted users`() {
        val publicId = createUser()
        mockMvc.perform(delete("/users/$publicId"))    // soft delete

        mockMvc.perform(get("/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // ── GET /users/{publicId} ──────────────────────────────────────────────────

    @Test
    fun `GET users by id - should return 200 with user`() {
        val publicId = createUser()

        mockMvc.perform(get("/users/$publicId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.publicId").value(publicId))
            .andExpect(jsonPath("$.name").value("Alice"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    @Test
    fun `GET users by id - should return 404 when user does not exist`() {
        mockMvc.perform(get("/users/${UUID.randomUUID()}"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("Not Found"))
    }

    // ── PATCH /users/{publicId}/deactivate ─────────────────────────────────────

    @Test
    fun `PATCH deactivate - should return 200 with INACTIVE status`() {
        val publicId = createUser()

        mockMvc.perform(patch("/users/$publicId/deactivate"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("INACTIVE"))
            .andExpect(jsonPath("$.publicId").value(publicId))
    }

    @Test
    fun `PATCH deactivate - should return 404 when user does not exist`() {
        mockMvc.perform(patch("/users/${UUID.randomUUID()}/deactivate"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("Not Found"))
    }

    @Test
    fun `PATCH deactivate - should return 422 when user is already INACTIVE`() {
        val publicId = createUser()
        mockMvc.perform(patch("/users/$publicId/deactivate"))     // first deactivation

        mockMvc.perform(patch("/users/$publicId/deactivate"))     // attempt again
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
    }

    // ── DELETE /users/{publicId} ───────────────────────────────────────────────

    @Test
    fun `DELETE user - should return 200 with DELETED status`() {
        val publicId = createUser()

        mockMvc.perform(delete("/users/$publicId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("DELETED"))
            .andExpect(jsonPath("$.publicId").value(publicId))
    }

    @Test
    fun `DELETE user - should return 404 when user does not exist`() {
        mockMvc.perform(delete("/users/${UUID.randomUUID()}"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("Not Found"))
    }

    @Test
    fun `DELETE user - should return 422 when user is already DELETED`() {
        val publicId = createUser()
        mockMvc.perform(delete("/users/$publicId"))                // first delete

        mockMvc.perform(delete("/users/$publicId"))                // attempt again
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
    }
}

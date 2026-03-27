package com.emailnotification.integration

import com.emailnotification.adapter.`in`.web.dto.CreateUserRequest
import com.emailnotification.adapter.out.persistence.EmailRequestJpaEntity
import com.emailnotification.adapter.out.persistence.EmailRequestJpaRepository
import com.emailnotification.adapter.out.persistence.EmailStatusEntryJpaRepository
import com.emailnotification.adapter.out.persistence.RetryControlJpaRepository
import com.emailnotification.adapter.out.persistence.UserJpaRepository
import com.emailnotification.domain.model.EmailEventType
import com.emailnotification.domain.model.EmailStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * End-to-end integration tests for the email notification flow.
 *
 * Verifies the complete path:
 * user endpoint -> domain event publication -> async listener -> SendEmailUseCase ->
 * persistence in email_request + email_status (+ retry_control when applicable).
 *
 * Uses real Spring beans (no mocks) with PostgreSQL via Testcontainers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailFlowIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @Autowired private lateinit var userJpaRepository: UserJpaRepository
    @Autowired private lateinit var emailRequestJpaRepository: EmailRequestJpaRepository
    @Autowired private lateinit var emailStatusEntryJpaRepository: EmailStatusEntryJpaRepository
    @Autowired private lateinit var retryControlJpaRepository: RetryControlJpaRepository

    @BeforeEach
    fun cleanDatabase() {
        // FK-safe cleanup order: retry_control -> email_status -> email_request -> users
        retryControlJpaRepository.deleteAll()
        emailStatusEntryJpaRepository.deleteAll()
        emailRequestJpaRepository.deleteAll()
        userJpaRepository.deleteAll()
    }

    @Test
    fun `POST users should persist USER_CREATED email request with SENT status`() {
        val email = "alice@example.com"
        val publicId = createUser(name = "Alice", email = email)

        val requests = awaitRequests(expectedCount = 1)
        val request = requests.single()

        assertEquals(EmailEventType.USER_CREATED, request.eventType)
        assertEquals(EmailStatus.SENT, request.status)
        assertEquals(email, request.recipientEmail)

        val user = userJpaRepository.findByPublicId(publicId).orElseThrow()
        assertEquals(user.id, request.userId)

        val entries = emailStatusEntryJpaRepository.findAll()
            .filter { it.emailRequestId == request.id }
            .sortedBy { it.occurredAt }

        assertEquals(2, entries.size)
        assertEquals(null, entries[0].previousStatus)
        assertEquals(EmailStatus.PENDING, entries[0].newStatus)
        assertEquals(EmailStatus.PENDING, entries[1].previousStatus)
        assertEquals(EmailStatus.SENT, entries[1].newStatus)

        // Stub sender succeeds, so retry table should remain empty.
        assertTrue(retryControlJpaRepository.findAll().isEmpty())
    }

    @Test
    fun `PATCH deactivate should persist USER_DEACTIVATED email request`() {
        val publicId = createUser(name = "Bob", email = "bob@example.com")

        mockMvc.perform(patch("/users/$publicId/deactivate"))
            .andExpect(status().isOk)

        val requests = awaitRequests(expectedCount = 2)
        val byEvent = requests.associateBy { it.eventType }

        assertTrue(byEvent.containsKey(EmailEventType.USER_CREATED))
        assertTrue(byEvent.containsKey(EmailEventType.USER_DEACTIVATED))
        assertEquals(EmailStatus.SENT, byEvent.getValue(EmailEventType.USER_DEACTIVATED).status)
    }

    @Test
    fun `DELETE user should persist USER_DELETED email request`() {
        val publicId = createUser(name = "Carol", email = "carol@example.com")

        mockMvc.perform(delete("/users/$publicId"))
            .andExpect(status().isOk)

        val requests = awaitRequests(expectedCount = 2)
        val byEvent = requests.associateBy { it.eventType }

        assertTrue(byEvent.containsKey(EmailEventType.USER_CREATED))
        assertTrue(byEvent.containsKey(EmailEventType.USER_DELETED))
        assertEquals(EmailStatus.SENT, byEvent.getValue(EmailEventType.USER_DELETED).status)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun createUser(name: String, email: String): UUID {
        val response = mockMvc.perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateUserRequest(name, email)))
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString

        return UUID.fromString(objectMapper.readTree(response).get("publicId").asText())
    }

    private fun awaitRequests(expectedCount: Int): List<EmailRequestJpaEntity> {
        val timeout = Duration.ofSeconds(6)
        val pollEvery = Duration.ofMillis(120)
        val start = Instant.now()

        while (Duration.between(start, Instant.now()) < timeout) {
            val requests = emailRequestJpaRepository.findAll().sortedBy { it.createdAt }

            if (requests.size >= expectedCount && requests.takeLast(expectedCount).all { it.status == EmailStatus.SENT }) {
                return requests
            }

            Thread.sleep(pollEvery.toMillis())
        }

        val snapshot = emailRequestJpaRepository.findAll().sortedBy { it.createdAt }
        throw AssertionError(
            "Timed out waiting for $expectedCount SENT email_request row(s). " +
                "Current count=${snapshot.size}, statuses=${snapshot.map { it.status }}"
        )
    }
}

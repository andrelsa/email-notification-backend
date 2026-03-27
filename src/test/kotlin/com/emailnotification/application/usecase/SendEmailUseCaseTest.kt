package com.emailnotification.application.usecase

import com.emailnotification.domain.model.EmailEventType
import com.emailnotification.domain.model.EmailRequest
import com.emailnotification.domain.model.EmailRequestId
import com.emailnotification.domain.model.EmailStatus
import com.emailnotification.domain.model.User
import com.emailnotification.domain.port.EmailDeliveryException
import com.emailnotification.domain.port.EmailRequestRepository
import com.emailnotification.domain.port.EmailSender
import com.emailnotification.domain.port.RenderedEmail
import com.emailnotification.domain.port.RetryControlRepository
import com.emailnotification.domain.port.TemplateRenderer
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SendEmailUseCaseTest {

    private companion object {
        const val CONFIGURED_MAX_ATTEMPTS = 7
    }

    private val emailSender: EmailSender = mockk()
    private val templateRenderer: TemplateRenderer = mockk()
    private val emailRequestRepository: EmailRequestRepository = mockk()
    private val retryControlRepository: RetryControlRepository = mockk()

    private lateinit var useCase: SendEmailUseCase

    @BeforeEach
    fun setUp() {
        useCase = SendEmailUseCase(
            emailSender             = emailSender,
            templateRenderer        = templateRenderer,
            emailRequestRepository  = emailRequestRepository,
            retryControlRepository  = retryControlRepository,
            retryIntervalMinutes    = 5L,
            retryMaxAttempts        = CONFIGURED_MAX_ATTEMPTS
        )
    }

    @Test
    fun `should reject non-positive retry max attempts configuration`() {
        assertThrows<IllegalArgumentException> {
            SendEmailUseCase(
                emailSender            = emailSender,
                templateRenderer       = templateRenderer,
                emailRequestRepository = emailRequestRepository,
                retryControlRepository = retryControlRepository,
                retryIntervalMinutes   = 5L,
                retryMaxAttempts       = 0
            )
        }
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    fun `should persist PENDING request, send, and transition to SENT on success`() {
        // given
        val user    = aUser()
        val pending = aSavedRequest(status = EmailStatus.PENDING)
        val sent    = aSavedRequest(status = EmailStatus.SENT)

        every { templateRenderer.render(EmailEventType.USER_CREATED, user) } returns aRenderedEmail()
        every { emailRequestRepository.save(any()) } returnsMany listOf(pending, sent)
        every { emailRequestRepository.saveStatusEntry(any()) } answers { firstArg() }
        justRun { emailSender.send(any()) }

        // when
        val result = useCase.execute(user, EmailEventType.USER_CREATED)

        // then
        assertEquals(EmailStatus.SENT, result.status)
        verify(exactly = 2) { emailRequestRepository.save(any()) }          // PENDING + SENT
        verify(exactly = 2) { emailRequestRepository.saveStatusEntry(any()) } // initial + transition
        verify(exactly = 0) { retryControlRepository.save(any()) }
    }

    // ── transient failure ────────────────────────────────────────────────────

    @Test
    fun `should transition to RETRYING and create RetryControl on transient RuntimeException`() {
        // given
        val user     = aUser()
        val pending  = aSavedRequest(status = EmailStatus.PENDING)
        val retrying = aSavedRequest(status = EmailStatus.RETRYING)

        every { templateRenderer.render(any(), any()) } returns aRenderedEmail()
        every { emailRequestRepository.save(any()) } returnsMany listOf(pending, retrying)
        every { emailRequestRepository.saveStatusEntry(any()) } answers { firstArg() }
        every { emailSender.send(any()) } throws RuntimeException("connection timeout")
        every { retryControlRepository.save(any()) } answers { firstArg() }

        // when
        val result = useCase.execute(user, EmailEventType.USER_CREATED)

        // then
        assertEquals(EmailStatus.RETRYING, result.status)
        verify(exactly = 2) { emailRequestRepository.save(any()) }
        verify(exactly = 1) {
            retryControlRepository.save(match { it.maxAttempts == CONFIGURED_MAX_ATTEMPTS })
        }
        verify(exactly = 2) { emailRequestRepository.saveStatusEntry(any()) }
    }

    @Test
    fun `should transition to RETRYING on transient EmailDeliveryException (isPermanent=false)`() {
        // given
        val user     = aUser()
        val pending  = aSavedRequest(status = EmailStatus.PENDING)
        val retrying = aSavedRequest(status = EmailStatus.RETRYING)

        every { templateRenderer.render(any(), any()) } returns aRenderedEmail()
        every { emailRequestRepository.save(any()) } returnsMany listOf(pending, retrying)
        every { emailRequestRepository.saveStatusEntry(any()) } answers { firstArg() }
        every { emailSender.send(any()) } throws EmailDeliveryException("smtp unavailable", isPermanent = false)
        every { retryControlRepository.save(any()) } answers { firstArg() }

        // when
        val result = useCase.execute(user, EmailEventType.USER_DEACTIVATED)

        // then
        assertEquals(EmailStatus.RETRYING, result.status)
        verify(exactly = 1) {
            retryControlRepository.save(match { it.maxAttempts == CONFIGURED_MAX_ATTEMPTS })
        }
    }

    // ── permanent failure ────────────────────────────────────────────────────

    @Test
    fun `should transition directly to FAILED on permanent EmailDeliveryException`() {
        // given
        val user   = aUser()
        val pending = aSavedRequest(status = EmailStatus.PENDING)
        val failed  = aSavedRequest(status = EmailStatus.FAILED)

        every { templateRenderer.render(any(), any()) } returns aRenderedEmail()
        every { emailRequestRepository.save(any()) } returnsMany listOf(pending, failed)
        every { emailRequestRepository.saveStatusEntry(any()) } answers { firstArg() }
        every { emailSender.send(any()) } throws EmailDeliveryException("invalid address", isPermanent = true)

        // when
        val result = useCase.execute(user, EmailEventType.USER_DELETED)

        // then
        assertEquals(EmailStatus.FAILED, result.status)
        verify(exactly = 0) { retryControlRepository.save(any()) }
        verify(exactly = 2) { emailRequestRepository.saveStatusEntry(any()) } // initial PENDING + FAILED
    }

    // ── guard: null user id ──────────────────────────────────────────────────

    @Test
    fun `should throw IllegalArgumentException when user has no persisted id`() {
        // given — user without a DB-assigned id (not yet persisted)
        val unpersisted = User(id = null, name = "Alice", email = "alice@example.com")

        // when / then — requireNotNull throws IllegalArgumentException
        assertThrows<IllegalArgumentException> {
            useCase.execute(unpersisted, EmailEventType.USER_CREATED)
        }
        verify(exactly = 0) { templateRenderer.render(any(), any()) }
        verify(exactly = 0) { emailRequestRepository.save(any()) }
    }

    // ── test data factories ──────────────────────────────────────────────────

    private fun aUser(id: Long = 1L) = User(
        id    = id,
        name  = "Alice",
        email = "alice@example.com"
    )

    private fun aRenderedEmail() = RenderedEmail(
        subject = "Test subject",
        body    = "Test body"
    )

    private fun aSavedRequest(
        id: Long = 10L,
        status: EmailStatus = EmailStatus.PENDING
    ) = EmailRequest(
        id             = id,
        publicId       = EmailRequestId.generate(),
        userId         = 1L,
        recipientEmail = "alice@example.com",
        subject        = "Test subject",
        body           = "Test body",
        eventType      = EmailEventType.USER_CREATED,
        status         = status
    )
}


package com.emailnotification.application.usecase

import com.emailnotification.domain.model.EmailEventType
import com.emailnotification.domain.model.EmailRequest
import com.emailnotification.domain.model.EmailRequestId
import com.emailnotification.domain.model.EmailStatus
import com.emailnotification.domain.model.RetryControl
import com.emailnotification.domain.port.EmailDeliveryException
import com.emailnotification.domain.port.EmailRequestRepository
import com.emailnotification.domain.port.EmailSender
import com.emailnotification.domain.port.RetryControlRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ProcessRetryUseCaseTest {

    private val emailSender: EmailSender = mockk()
    private val emailRequestRepository: EmailRequestRepository = mockk()
    private val retryControlRepository: RetryControlRepository = mockk()

    private lateinit var useCase: ProcessRetryUseCase

    @BeforeEach
    fun setUp() {
        useCase = ProcessRetryUseCase(
            emailSender            = emailSender,
            emailRequestRepository = emailRequestRepository,
            retryControlRepository = retryControlRepository,
            retryIntervalMinutes   = 5L
        )
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    fun `should transition EmailRequest to SENT on successful retry`() {
        // given
        val retryControl = aRetryControl(attemptCount = 1)
        val retrying     = aSavedRequest(status = EmailStatus.RETRYING)
        val sent         = aSavedRequest(status = EmailStatus.SENT)

        every { retryControlRepository.tryClaimForProcessing(retryControl.id!!, any()) } returns true
        every { emailRequestRepository.findById(retryControl.emailRequestId) } returns retrying
        justRun { emailSender.send(any()) }
        every { emailRequestRepository.save(match { it.status == EmailStatus.SENT }) } returns sent
        every { emailRequestRepository.saveStatusEntry(any()) } answers { firstArg() }

        // when
        useCase.execute(retryControl)

        // then
        verify(exactly = 1) { emailRequestRepository.save(match { it.status == EmailStatus.SENT }) }
        verify(exactly = 1) { emailRequestRepository.saveStatusEntry(
            match { it.newStatus == EmailStatus.SENT && it.previousStatus == EmailStatus.RETRYING }
        )}
        verify(exactly = 0) { retryControlRepository.save(any()) }
    }

    // ── guard: already claimed by another instance ────────────────────────────

    @Test
    fun `should skip silently when retry is already claimed by another instance`() {
        // given — another instance holds the lock
        val retryControl = aRetryControl()
        every { retryControlRepository.tryClaimForProcessing(retryControl.id!!, any()) } returns false

        // when
        useCase.execute(retryControl)

        // then — no DB reads or writes, no email send
        verify(exactly = 0) { emailRequestRepository.findById(any()) }
        verify(exactly = 0) { emailSender.send(any()) }
        verify(exactly = 0) { emailRequestRepository.save(any()) }
        verify(exactly = 0) { emailRequestRepository.saveStatusEntry(any()) }
    }

    // ── guard: email request not found ───────────────────────────────────────

    @Test
    fun `should skip silently when EmailRequest is not found`() {
        // given
        val retryControl = aRetryControl()
        every { retryControlRepository.tryClaimForProcessing(retryControl.id!!, any()) } returns true
        every { emailRequestRepository.findById(retryControl.emailRequestId) } returns null

        // when
        useCase.execute(retryControl)

        // then — no send, no DB writes
        verify(exactly = 0) { emailSender.send(any()) }
        verify(exactly = 0) { emailRequestRepository.save(any()) }
        verify(exactly = 0) { emailRequestRepository.saveStatusEntry(any()) }
    }

    // ── guard: email request no longer RETRYING ───────────────────────────────

    @Test
    fun `should skip silently when EmailRequest is no longer RETRYING`() {
        // given — already SENT by a concurrent process
        val retryControl = aRetryControl()
        val alreadySent  = aSavedRequest(status = EmailStatus.SENT)
        every { retryControlRepository.tryClaimForProcessing(retryControl.id!!, any()) } returns true
        every { emailRequestRepository.findById(retryControl.emailRequestId) } returns alreadySent

        // when
        useCase.execute(retryControl)

        // then — no send, no DB writes
        verify(exactly = 0) { emailSender.send(any()) }
        verify(exactly = 0) { emailRequestRepository.save(any()) }
    }

    // ── permanent failure ────────────────────────────────────────────────────

    @Test
    fun `should transition to FAILED on permanent EmailDeliveryException`() {
        // given
        val retryControl = aRetryControl(attemptCount = 0, maxAttempts = 3)
        val retrying     = aSavedRequest(status = EmailStatus.RETRYING)
        val failed       = aSavedRequest(status = EmailStatus.FAILED)

        every { retryControlRepository.tryClaimForProcessing(retryControl.id!!, any()) } returns true
        every { emailRequestRepository.findById(retryControl.emailRequestId) } returns retrying
        every { emailSender.send(any()) } throws EmailDeliveryException("invalid address", isPermanent = true)
        every { emailRequestRepository.save(match { it.status == EmailStatus.FAILED }) } returns failed
        every { emailRequestRepository.saveStatusEntry(any()) } answers { firstArg() }
        every { retryControlRepository.save(any()) } answers { firstArg() }

        // when
        useCase.execute(retryControl)

        // then
        verify(exactly = 1) { emailRequestRepository.save(match { it.status == EmailStatus.FAILED }) }
        verify(exactly = 1) { emailRequestRepository.saveStatusEntry(
            match { it.newStatus == EmailStatus.FAILED && it.previousStatus == EmailStatus.RETRYING }
        )}
        verify(exactly = 1) { retryControlRepository.save(any()) }
    }

    // ── max attempts exceeded ─────────────────────────────────────────────────

    @Test
    fun `should transition to FAILED when max retry attempts are exceeded`() {
        // given — attemptCount=2, maxAttempts=3: after recordFailedAttempt → 3 >= 3 = exceeded
        val retryControl = aRetryControl(attemptCount = 2, maxAttempts = 3)
        val retrying     = aSavedRequest(status = EmailStatus.RETRYING)
        val failed       = aSavedRequest(status = EmailStatus.FAILED)

        every { retryControlRepository.tryClaimForProcessing(retryControl.id!!, any()) } returns true
        every { emailRequestRepository.findById(retryControl.emailRequestId) } returns retrying
        every { emailSender.send(any()) } throws RuntimeException("smtp timeout")
        every { emailRequestRepository.save(match { it.status == EmailStatus.FAILED }) } returns failed
        every { emailRequestRepository.saveStatusEntry(any()) } answers { firstArg() }
        every { retryControlRepository.save(any()) } answers { firstArg() }

        // when
        useCase.execute(retryControl)

        // then
        verify(exactly = 1) { emailRequestRepository.save(match { it.status == EmailStatus.FAILED }) }
        verify(exactly = 1) { retryControlRepository.save(any()) }
    }

    // ── still retrying ────────────────────────────────────────────────────────

    @Test
    fun `should keep RETRYING and reschedule when attempts are not exhausted`() {
        // given — attemptCount=0, maxAttempts=3: after recordFailedAttempt → 1 < 3 = still retrying
        val retryControl = aRetryControl(attemptCount = 0, maxAttempts = 3)
        val retrying     = aSavedRequest(status = EmailStatus.RETRYING)

        every { retryControlRepository.tryClaimForProcessing(retryControl.id!!, any()) } returns true
        every { emailRequestRepository.findById(retryControl.emailRequestId) } returns retrying
        every { emailSender.send(any()) } throws RuntimeException("connection refused")
        every { emailRequestRepository.saveStatusEntry(any()) } answers { firstArg() }
        every { retryControlRepository.save(any()) } answers { firstArg() }

        // when
        useCase.execute(retryControl)

        // then — EmailRequest status unchanged (still RETRYING), only RetryControl is updated
        verify(exactly = 0) { emailRequestRepository.save(any()) }
        verify(exactly = 1) { emailRequestRepository.saveStatusEntry(
            match { it.newStatus == EmailStatus.RETRYING && it.previousStatus == EmailStatus.RETRYING }
        )}
        verify(exactly = 1) { retryControlRepository.save(
            match { it.attemptCount == 1 }
        )}
    }

    // ── test data factories ───────────────────────────────────────────────────

    private fun aRetryControl(
        emailRequestId: Long = 10L,
        attemptCount: Int    = 1,
        maxAttempts: Int     = 3
    ) = RetryControl(
        id             = 1L,
        emailRequestId = emailRequestId,
        attemptCount   = attemptCount,
        maxAttempts    = maxAttempts,
        nextAttemptAt  = LocalDateTime.now().minusMinutes(1) // overdue — ready to process
    )

    private fun aSavedRequest(
        id: Long = 10L,
        status: EmailStatus = EmailStatus.RETRYING
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


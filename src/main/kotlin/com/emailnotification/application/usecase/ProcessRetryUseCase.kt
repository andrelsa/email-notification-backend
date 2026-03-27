package com.emailnotification.application.usecase

import com.emailnotification.domain.model.EmailRequest
import com.emailnotification.domain.model.EmailStatus
import com.emailnotification.domain.model.EmailStatusEntry
import com.emailnotification.domain.model.RetryControl
import com.emailnotification.domain.port.EmailDeliveryException
import com.emailnotification.domain.port.EmailRequestRepository
import com.emailnotification.domain.port.EmailSender
import com.emailnotification.domain.port.RetryControlRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Processes a single retry attempt for an [EmailRequest] that previously failed.
 *
 * Called by the retry scheduler ([com.emailnotification.adapter.in.scheduler.RetryScheduler])
 * for each [RetryControl] record whose [RetryControl.nextAttemptAt] has elapsed.
 *
 * ## Flow
 * 1. Load the [EmailRequest] linked to [retryControl].
 * 2. Guard: if the request is no longer [EmailStatus.RETRYING], skip silently
 *    (may have been resolved by a concurrent process).
 * 3. Attempt delivery via [EmailSender].
 *    - Success → [EmailStatus.SENT], audit entry.
 *    - Permanent failure → [EmailStatus.FAILED], audit entry, update [RetryControl].
 *    - Transient failure + attempts not exhausted
 *                       → keep [EmailStatus.RETRYING], audit entry, update [RetryControl].
 *    - Transient failure + max attempts exceeded
 *                       → [EmailStatus.FAILED], audit entry, update [RetryControl].
 *
 * @param retryIntervalMinutes minutes to wait before the next retry attempt (default: 5).
 */
@Service
@Transactional
class ProcessRetryUseCase(
    private val emailSender: EmailSender,
    private val emailRequestRepository: EmailRequestRepository,
    private val retryControlRepository: RetryControlRepository,
    @Value("\${app.email.retry.interval-minutes:5}") private val retryIntervalMinutes: Long
) {

    private val log = LoggerFactory.getLogger(ProcessRetryUseCase::class.java)

    /**
     * Executes one retry attempt for the email request associated with [retryControl].
     *
     * @param retryControl the retry record that is ready for a new attempt
     *                     (i.e. [RetryControl.isReadyToRetry] returned true when selected
     *                     by [RetryControlRepository.findAllReadyForRetry]).
     */
    fun execute(retryControl: RetryControl) {
        val emailRequest = emailRequestRepository.findById(retryControl.emailRequestId)
        if (emailRequest == null) {
            log.warn(
                "EmailRequest id={} not found for retry (retryControl={}), skipping",
                retryControl.emailRequestId, retryControl.id
            )
            return
        }

        if (!emailRequest.isRetrying()) {
            log.info(
                "EmailRequest {} is no longer RETRYING (status={}), skipping retry",
                emailRequest.publicId, emailRequest.status
            )
            return
        }

        val attemptNumber = retryControl.attemptCount + 1
        log.info(
            "Retry attempt #{} for emailRequest={} (max={})",
            attemptNumber, emailRequest.publicId, retryControl.maxAttempts
        )

        try {
            emailSender.send(emailRequest)
            handleRetrySuccess(emailRequest)
        } catch (ex: Exception) {
            val isPermanent = ex is EmailDeliveryException && ex.isPermanent
            val errorMessage = ex.message ?: "Unknown error during retry"
            val updatedRetry = retryControl.recordFailedAttempt(
                errorMessage = errorMessage,
                nextAttemptAt = LocalDateTime.now().plusMinutes(retryIntervalMinutes)
            )

            if (isPermanent || updatedRetry.hasExceededMaxAttempts()) {
                val reason = if (isPermanent)
                    "Permanent failure: $errorMessage"
                else
                    "Max retry attempts (${retryControl.maxAttempts}) exceeded: $errorMessage"

                log.warn("EmailRequest {} permanently failed: {}", emailRequest.publicId, reason)
                handleRetryExhausted(emailRequest, updatedRetry, reason)
            } else {
                log.warn(
                    "Retry attempt #{} failed for emailRequest={}, next attempt at {}: {}",
                    attemptNumber, emailRequest.publicId, updatedRetry.nextAttemptAt, errorMessage
                )
                handleRetryRescheduled(emailRequest, updatedRetry, attemptNumber, errorMessage)
            }
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun handleRetrySuccess(emailRequest: EmailRequest) {
        val sent = emailRequest.markAsSent()
        val updated = emailRequestRepository.save(sent)
        emailRequestRepository.saveStatusEntry(
            EmailStatusEntry.transition(
                emailRequestId = updated.id!!,
                from = EmailStatus.RETRYING,
                to = EmailStatus.SENT,
                description = "Email delivered successfully on retry"
            )
        )
        log.info("Email delivered on retry: emailRequest={}", updated.publicId)
    }

    private fun handleRetryExhausted(
        emailRequest: EmailRequest,
        updatedRetry: RetryControl,
        reason: String
    ) {
        val failed = emailRequest.markAsFailed()
        val updated = emailRequestRepository.save(failed)
        emailRequestRepository.saveStatusEntry(
            EmailStatusEntry.transition(
                emailRequestId = updated.id!!,
                from = EmailStatus.RETRYING,
                to = EmailStatus.FAILED,
                description = reason
            )
        )
        retryControlRepository.save(updatedRetry)
    }

    private fun handleRetryRescheduled(
        emailRequest: EmailRequest,
        updatedRetry: RetryControl,
        attemptNumber: Int,
        errorMessage: String
    ) {
        // EmailRequest status stays RETRYING — only RetryControl is updated
        emailRequestRepository.saveStatusEntry(
            EmailStatusEntry.transition(
                emailRequestId = emailRequest.id!!,
                from = EmailStatus.RETRYING,
                to = EmailStatus.RETRYING,
                description = "Retry attempt #$attemptNumber failed, " +
                    "next attempt at ${updatedRetry.nextAttemptAt}: $errorMessage"
            )
        )
        retryControlRepository.save(updatedRetry)
    }
}

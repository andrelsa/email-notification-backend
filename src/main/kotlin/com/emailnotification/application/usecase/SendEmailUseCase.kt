package com.emailnotification.application.usecase

import com.emailnotification.domain.model.EmailEventType
import com.emailnotification.domain.model.EmailRequest
import com.emailnotification.domain.model.EmailStatus
import com.emailnotification.domain.model.EmailStatusEntry
import com.emailnotification.domain.model.RetryControl
import com.emailnotification.domain.model.User
import com.emailnotification.domain.port.EmailDeliveryException
import com.emailnotification.domain.port.EmailRequestRepository
import com.emailnotification.domain.port.EmailSender
import com.emailnotification.domain.port.RetryControlRepository
import com.emailnotification.domain.port.TemplateRenderer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Orchestrates the first send attempt for an email notification triggered
 * by a user domain event.
 *
 * ## Flow
 * 1. Render subject + body via [TemplateRenderer].
 * 2. Persist an [EmailRequest] with status [EmailStatus.PENDING].
 * 3. Record the initial audit entry ([EmailStatusEntry]).
 * 4. Attempt delivery via [EmailSender].
 *    - Success  → transition to [EmailStatus.SENT], record audit entry.
 *    - Permanent failure ([EmailDeliveryException.isPermanent] = true)
 *                → transition to [EmailStatus.FAILED], record audit entry.
 *    - Transient failure → transition to [EmailStatus.RETRYING],
 *                          create [RetryControl], record audit entry.
 *
 * Retry attempts are handled by [ProcessRetryUseCase] via the scheduler.
 *
 * @param retryIntervalMinutes minutes to wait before the first retry attempt (default: 5).
 */
@Service
@Transactional
class SendEmailUseCase(
    private val emailSender: EmailSender,
    private val templateRenderer: TemplateRenderer,
    private val emailRequestRepository: EmailRequestRepository,
    private val retryControlRepository: RetryControlRepository,
    @Value("\${app.email.retry.interval-minutes:5}") private val retryIntervalMinutes: Long,
    @Value("\${app.email.retry.max-attempts:3}") private val retryMaxAttempts: Int
) {

    init {
        require(retryMaxAttempts > 0) {
            "Property app.email.retry.max-attempts must be > 0, but was $retryMaxAttempts"
        }
    }

    private val log = LoggerFactory.getLogger(SendEmailUseCase::class.java)

    /**
     * Executes the first email send attempt for the given [user] and [eventType].
     *
     * @param user      the user whose lifecycle event triggered the notification;
     *                  must be a persisted entity (non-null [User.id]).
     * @param eventType the type of event that triggered this notification.
     * @return the persisted [EmailRequest] in its final status after the first attempt.
     */
    fun execute(user: User, eventType: EmailEventType): EmailRequest {
        val userId = requireNotNull(user.id) {
            "User must be persisted (non-null id) before sending email notification"
        }

        log.info("Sending email for event={} user={}", eventType, user.publicId)

        // 1. Render template
        val rendered = templateRenderer.render(eventType, user)

        // 2. Persist EmailRequest (PENDING)
        val emailRequest = EmailRequest(
            userId = userId,
            recipientEmail = user.email,
            subject = rendered.subject,
            body = rendered.body,
            eventType = eventType
        )
        val saved = emailRequestRepository.save(emailRequest)

        // 3. Initial audit entry: null -> PENDING
        emailRequestRepository.saveStatusEntry(
            EmailStatusEntry.initial(
                emailRequestId = saved.id!!,
                description = "Email request created for event $eventType"
            )
        )

        // 4. Attempt delivery
        return try {
            emailSender.send(saved)
            handleSuccess(saved, EmailStatus.PENDING)
        } catch (ex: EmailDeliveryException) {
            val message = ex.message ?: "Email delivery failed"
            if (ex.isPermanent) {
                log.warn("Permanent delivery failure for emailRequest={}: {}", saved.publicId, message)
                handlePermanentFailure(saved, EmailStatus.PENDING, message)
            } else {
                log.warn("Transient delivery failure for emailRequest={}: {}", saved.publicId, message)
                handleTransientFailure(saved, EmailStatus.PENDING, message)
            }
        } catch (ex: Exception) {
            val message = ex.message ?: "Unexpected error during email delivery"
            log.error("Unexpected failure for emailRequest={}: {}", saved.publicId, message, ex)
            handleTransientFailure(saved, EmailStatus.PENDING, message)
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun handleSuccess(emailRequest: EmailRequest, previousStatus: EmailStatus): EmailRequest {
        val sent = emailRequest.markAsSent()
        val updated = emailRequestRepository.save(sent)
        val requestId = updated.id!!
        emailRequestRepository.saveStatusEntry(
            EmailStatusEntry.transition(
                emailRequestId = requestId,
                from = previousStatus,
                to = EmailStatus.SENT,
                description = "Email delivered successfully"
            )
        )
        log.info("Email delivered: emailRequest={}", updated.publicId)
        return updated
    }

    private fun handlePermanentFailure(
        emailRequest: EmailRequest,
        previousStatus: EmailStatus,
        errorMessage: String
    ): EmailRequest {
        val failed = emailRequest.markAsFailed()
        val updated = emailRequestRepository.save(failed)
        val requestId = updated.id!!
        emailRequestRepository.saveStatusEntry(
            EmailStatusEntry.transition(
                emailRequestId = requestId,
                from = previousStatus,
                to = EmailStatus.FAILED,
                description = "Permanent failure: $errorMessage"
            )
        )
        return updated
    }

    private fun handleTransientFailure(
        emailRequest: EmailRequest,
        previousStatus: EmailStatus,
        errorMessage: String
    ): EmailRequest {
        val retrying = emailRequest.markAsRetrying()
        val updated = emailRequestRepository.save(retrying)
        val requestId = updated.id!!

        emailRequestRepository.saveStatusEntry(
            EmailStatusEntry.transition(
                emailRequestId = requestId,
                from = previousStatus,
                to = EmailStatus.RETRYING,
                description = "Transient failure — scheduled for retry: $errorMessage"
            )
        )

        val nextAttemptAt = LocalDateTime.now().plusMinutes(retryIntervalMinutes)
        retryControlRepository.save(
            RetryControl(
                emailRequestId = requestId,
                maxAttempts = retryMaxAttempts,
                nextAttemptAt = nextAttemptAt
            )
        )

        log.info("EmailRequest {} scheduled for retry at {}", updated.publicId, nextAttemptAt)
        return updated
    }
}

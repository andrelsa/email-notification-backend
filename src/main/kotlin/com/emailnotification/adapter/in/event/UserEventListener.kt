package com.emailnotification.adapter.`in`.event

import com.emailnotification.application.usecase.SendEmailUseCase
import com.emailnotification.config.AsyncConfig
import com.emailnotification.domain.event.UserCreatedEvent
import com.emailnotification.domain.event.UserDeactivatedEvent
import com.emailnotification.domain.event.UserDeletedEvent
import com.emailnotification.domain.model.EmailEventType
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Input adapter — listens to user domain events and triggers email notifications.
 *
 * ## Design (architectural decision D2)
 * Each handler is annotated with both:
 * - [@TransactionalEventListener(AFTER_COMMIT)][TransactionalEventListener] — guarantees the
 *   listener fires **only after** the originating user transaction has committed successfully.
 *   If the transaction rolls back, no email is sent.
 * - [@Async][Async] — the handler executes in a background thread, so the HTTP response is
 *   returned to the caller immediately without waiting for email operations to complete.
 *
 * [SendEmailUseCase] is `@Transactional` and will open its **own** new transaction in the
 * background thread (no parent transaction exists in an async context).
 *
 * ## Error handling
 * Each handler uses [runCatching] to absorb any exception thrown by [SendEmailUseCase].
 * Failures are logged as errors; the [com.emailnotification.domain.model.EmailRequest] will
 * be in [com.emailnotification.domain.model.EmailStatus.RETRYING] or
 * [com.emailnotification.domain.model.EmailStatus.PENDING] state and the retry scheduler
 * (T3.8) will reprocess it.
 */
@Component
class UserEventListener(
    private val sendEmailUseCase: SendEmailUseCase
) {

    private val log = LoggerFactory.getLogger(UserEventListener::class.java)

    /**
     * Sends a welcome email after a new user is created and committed.
     */
    @Async(AsyncConfig.EVENT_ASYNC_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUserCreated(event: UserCreatedEvent) {
        log.debug("Received UserCreatedEvent for user={}", event.user.publicId)
        runCatching {
            sendEmailUseCase.execute(event.user, EmailEventType.USER_CREATED)
        }.onFailure { ex ->
            log.error(
                "Unhandled error sending email for UserCreatedEvent user={}: {}",
                event.user.publicId, ex.message, ex
            )
        }
    }

    /**
     * Sends a deactivation notification after a user is deactivated and committed.
     */
    @Async(AsyncConfig.EVENT_ASYNC_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUserDeactivated(event: UserDeactivatedEvent) {
        log.debug("Received UserDeactivatedEvent for user={}", event.user.publicId)
        runCatching {
            sendEmailUseCase.execute(event.user, EmailEventType.USER_DEACTIVATED)
        }.onFailure { ex ->
            log.error(
                "Unhandled error sending email for UserDeactivatedEvent user={}: {}",
                event.user.publicId, ex.message, ex
            )
        }
    }

    /**
     * Sends a deletion confirmation email after a user is soft-deleted and committed.
     */
    @Async(AsyncConfig.EVENT_ASYNC_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUserDeleted(event: UserDeletedEvent) {
        log.debug("Received UserDeletedEvent for user={}", event.user.publicId)
        runCatching {
            sendEmailUseCase.execute(event.user, EmailEventType.USER_DELETED)
        }.onFailure { ex ->
            log.error(
                "Unhandled error sending email for UserDeletedEvent user={}: {}",
                event.user.publicId, ex.message, ex
            )
        }
    }
}


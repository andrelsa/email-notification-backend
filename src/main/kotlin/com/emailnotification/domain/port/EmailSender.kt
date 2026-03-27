package com.emailnotification.domain.port

import com.emailnotification.domain.model.EmailRequest

/**
 * Output port for sending email notifications.
 *
 * Defined in the domain layer — no transport/SMTP framework dependency.
 *
 * Implementations:
 *   - [com.emailnotification.adapter.out.email.StubEmailSender] — logs to console (local/test profile)
 *   - Future: SmtpEmailSender, SesEmailSender, etc. (prod profile)
 *
 * Throwing any [Exception] from [send] signals a delivery failure and triggers
 * the retry/failure flow in the use case layer.
 */
interface EmailSender {

    /**
     * Attempts to send the email described by [emailRequest].
     *
     * The [emailRequest] already contains the fully-rendered [EmailRequest.subject]
     * and [EmailRequest.body]; the sender is responsible only for transport.
     *
     * @throws Exception on delivery failure (any subtype is treated as a transient error
     *   by the use case layer unless it is an [EmailDeliveryException] with
     *   [EmailDeliveryException.isPermanent] set to true).
     */
    fun send(emailRequest: EmailRequest)
}

/**
 * Signals a failure to deliver an email notification.
 *
 * @param message human-readable description of the failure.
 * @param isPermanent when true the error is not retryable (e.g. invalid address);
 *   the request should transition directly to FAILED without incrementing retry count.
 * @param cause the underlying exception, if any.
 */
class EmailDeliveryException(
    message: String,
    val isPermanent: Boolean = false,
    cause: Throwable? = null
) : RuntimeException(message, cause)

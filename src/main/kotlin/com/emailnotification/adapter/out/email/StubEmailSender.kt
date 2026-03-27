package com.emailnotification.adapter.out.email

import com.emailnotification.domain.model.EmailRequest
import com.emailnotification.domain.port.EmailSender
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Stub implementation of [EmailSender] for local development and testing.
 *
 * Instead of sending a real email, this adapter logs all notification details
 * to the application log at INFO level. No external dependencies required.
 *
 * Active in all Spring profiles **except** `prod`.
 * In a production environment, replace this with a real transport adapter
 * (e.g. SmtpEmailSender, SesEmailSender) and activate it via the `prod` profile.
 *
 * Risk R3 mitigation: this bean is explicitly excluded from the `prod` profile so
 * a missing real implementation causes a startup failure rather than silent stub usage.
 */
@Component
@Profile("!prod")
class StubEmailSender : EmailSender {

    private val log = LoggerFactory.getLogger(StubEmailSender::class.java)

    /**
     * Simulates email delivery by logging all relevant fields.
     * Never throws — the stub always succeeds.
     */
    override fun send(emailRequest: EmailRequest) {
        log.info(
            """
            |
            |╔══════════════════════════════════════════════════════
            |║  [STUB] Email Notification
            |╠══════════════════════════════════════════════════════
            |║  publicId   : {}
            |║  eventType  : {}
            |║  recipient  : {}
            |║  subject    : {}
            |║  body       :
            |{}
            |╚══════════════════════════════════════════════════════
            """.trimMargin(),
            emailRequest.publicId,
            emailRequest.eventType,
            emailRequest.recipientEmail,
            emailRequest.subject,
            emailRequest.body.lines().joinToString("\n") { "|    $it" }
        )
    }
}


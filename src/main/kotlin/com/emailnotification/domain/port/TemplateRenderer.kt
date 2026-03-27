package com.emailnotification.domain.port

import com.emailnotification.domain.model.EmailEventType
import com.emailnotification.domain.model.User

/**
 * Output port for rendering email notification content.
 *
 * Defined in the domain layer — no template engine dependency.
 *
 * Implementations:
 *   - [com.emailnotification.adapter.out.template.InMemoryTemplateRenderer] — hardcoded strings (all profiles)
 *   - Future: ThymeleafTemplateRenderer, external template-service client, etc.
 */
interface TemplateRenderer {

    /**
     * Renders the email subject and body for the given [eventType] and [user].
     *
     * @param eventType the domain event that triggered this notification.
     * @param user      the user whose lifecycle event triggered the notification.
     * @return a [RenderedEmail] containing the fully resolved subject and body.
     */
    fun render(eventType: EmailEventType, user: User): RenderedEmail
}

/**
 * Value object holding the fully rendered content of an email notification.
 *
 * @param subject the email subject line (plain text, max 500 chars per schema).
 * @param body    the email body (plain text or HTML depending on renderer implementation).
 */
data class RenderedEmail(
    val subject: String,
    val body: String
)

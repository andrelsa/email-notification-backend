package com.emailnotification.adapter.out.template

import com.emailnotification.domain.model.EmailEventType
import com.emailnotification.domain.model.User
import com.emailnotification.domain.port.RenderedEmail
import com.emailnotification.domain.port.TemplateRenderer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * In-memory implementation of [TemplateRenderer].
 *
 * Renders email subjects and bodies from hardcoded string templates for each
 * [EmailEventType]. No external template engine or file dependency is required.
 *
 * Active in **all** Spring profiles.
 * Replace with a real renderer (e.g. ThymeleafTemplateRenderer or an external
 * template-service client) when richer templates or i18n support is needed.
 */
@Component
class InMemoryTemplateRenderer : TemplateRenderer {

    private val log = LoggerFactory.getLogger(InMemoryTemplateRenderer::class.java)

    override fun render(eventType: EmailEventType, user: User): RenderedEmail {
        log.debug("Rendering template for event={} user={}", eventType, user.publicId)
        return when (eventType) {
            EmailEventType.USER_CREATED     -> renderUserCreated(user)
            EmailEventType.USER_DEACTIVATED -> renderUserDeactivated(user)
            EmailEventType.USER_DELETED     -> renderUserDeleted(user)
        }
    }

    // ── Templates ────────────────────────────────────────────────────────────

    private fun renderUserCreated(user: User): RenderedEmail = RenderedEmail(
        subject = "Bem-vindo(a), ${user.name}! Sua conta foi criada.",
        body = """
            Olá, ${user.name}!

            Seja bem-vindo(a) ao sistema de notificações por e-mail.
            Sua conta foi criada com sucesso e já está ativa.

            Detalhes da conta:
              - Nome : ${user.name}
              - E-mail: ${user.email}
              - Status: ATIVO

            Caso não reconheça este cadastro, entre em contato com o suporte.

            Atenciosamente,
            Equipe de Notificações
        """.trimIndent()
    )

    private fun renderUserDeactivated(user: User): RenderedEmail = RenderedEmail(
        subject = "Sua conta foi desativada, ${user.name}.",
        body = """
            Olá, ${user.name}!

            Informamos que sua conta foi desativada.
            Você não receberá mais notificações enquanto sua conta estiver inativa.

            Detalhes da conta:
              - Nome : ${user.name}
              - E-mail: ${user.email}
              - Status: INATIVO

            Caso acredite que isso foi um engano ou deseje reativar sua conta,
            entre em contato com o suporte.

            Atenciosamente,
            Equipe de Notificações
        """.trimIndent()
    )

    private fun renderUserDeleted(user: User): RenderedEmail = RenderedEmail(
        subject = "Sua conta foi removida, ${user.name}.",
        body = """
            Olá, ${user.name}!

            Confirmamos que sua conta foi removida do sistema.
            Todos os seus dados serão tratados conforme nossa política de privacidade.

            Detalhes da conta encerrada:
              - Nome : ${user.name}
              - E-mail: ${user.email}
              - Status: REMOVIDO

            Se você não solicitou a remoção desta conta,
            entre em contato imediatamente com o suporte.

            Atenciosamente,
            Equipe de Notificações
        """.trimIndent()
    )
}


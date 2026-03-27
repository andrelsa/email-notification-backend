# Plan: Email Notification Backend — Plano Técnico Completo

---

## 1. Resumo do Objetivo

Construir um backend em **Kotlin + Spring Boot + Gradle** que gerencia usuários (CRUD) e dispara notificações por e-mail em resposta a eventos de domínio (`UserCreated`, `UserDeactivated`, `UserDeleted`). O serviço persiste histórico de envio, status e retries em **PostgreSQL** (migrado com **Flyway**), roda em **Docker Compose** e é projetado com **Clean Architecture** para permitir futura integração com um template-service externo.

---

## 2. Premissas e Restrições

- **Greenfield** — o repositório não possui código ainda; tudo será criado do zero.
- **Monorepo single-module** — suficiente para a escala atual; modularização Gradle futura se necessário.
- **E-mail via stub** — nesta fase o `EmailSender` será um stub (log/console); a porta (interface) já estará pronta para SMTP real.
- **Template interno** — textos de e-mail são strings em código; a porta `TemplateRenderer` será criada mas a implementação externa fica para fase futura.
- **Sem mensageria** — eventos de domínio via `ApplicationEventPublisher` do Spring (in-process). Migração para RabbitMQ/Kafka fica para fase futura.
- **Java 17+** como target JVM, Kotlin 1.9+.
- **Ambiente local** orquestrado exclusivamente via `docker-compose`.

---

## 3. Decisões Arquiteturais

| # | Decisão | Justificativa |
|---|---------|---------------|
| D1 | **Clean Architecture em 4 camadas** (`domain`, `application`, `adapter.in`, `adapter.out`) dentro de um único módulo Gradle | Baixo acoplamento sem overhead de multi-module prematuro. Pacotes substituem módulos. |
| D2 | **Eventos de domínio via `ApplicationEventPublisher`** | Desacopla o CRUD de usuário do envio de e-mail sem precisar de broker externo agora. Listener assíncrono com `@Async` + `@TransactionalEventListener(phase = AFTER_COMMIT)` garante que o e-mail só é disparado após commit. |
| D3 | **Portas e Adaptadores (Hexagonal)** para `EmailSender` e `TemplateRenderer` | Permite trocar stub → SMTP e stub → template-service externo sem alterar domínio/application. |
| D4 | **Retry com tabela `retry_control`** + scheduled job (`@Scheduled`) | Solução simples e auditável; evita dependência de lib de retry externa nesta fase. |
| D5 | **Flyway** para migrações de schema | Versionamento declarativo do banco, reprodutível em qualquer ambiente. |
| D6 | **UUID como identificador público** de todas as entidades | Evita exposição de IDs sequenciais; campo `id: Long` interno como PK do banco. |
| D7 | **Status machine de e-mail**: `PENDING → SENT`, `PENDING → RETRYING → SENT`, `RETRYING → FAILED` | Cobre happy-path e falhas com rastreabilidade. |
| D8 | **Docker Compose** com serviços `app` e `postgres` | Ambiente local reprodutível, alinhado ao doc de arquitetura. |

---

## 4. Plano em Fases

### Fase 1 — Fundação (Bootstrap & Infraestrutura)
Scaffold do projeto Spring Boot, Docker Compose, Flyway, health check.

### Fase 2 — Domínio de Usuário + Eventos
CRUD de `User`, publicação de eventos de domínio, testes unitários do domínio.

### Fase 3 — Domínio de E-mail + Retry
Listener de eventos, persistência de `EmailRequest`, máquina de status, job de retry, stub de envio.

---

## 5. Backlog Técnico (Checklist por Tarefa)

### Fase 1 — Fundação ✅

- [x] **T1.1** — Inicializar projeto Spring Boot com Kotlin DSL Gradle
- [x] **T1.2** — Configurar estrutura de pacotes (Clean Architecture)
- [x] **T1.3** — Criar `docker-compose.yml` (app + postgres)
- [x] **T1.4** — Criar `Dockerfile` multi-stage para a aplicação
- [x] **T1.5** — Configurar `application.yml` (datasource, flyway, profiles)
- [x] **T1.6** — Criar migrations Flyway (V1 — tabelas `users`, `email_request`, `email_status`, `retry_control`)
- [x] **T1.7** — Adicionar actuator + endpoint `/health`

### Fase 2 — Domínio de Usuário

- [ ] **T2.1** — Criar entidade de domínio `User` e value objects
- [ ] **T2.2** — Criar porta `UserRepository` (interface no domínio)
- [ ] **T2.3** — Criar use cases: `CreateUser`, `DeactivateUser`, `DeleteUser`, `GetUser`, `ListUsers`
- [ ] **T2.4** — Criar adaptador out `UserJpaRepository` + JPA entity `UserJpaEntity` + mapper
- [ ] **T2.5** — Criar adaptador in `UserController` (REST endpoints)
- [ ] **T2.6** — Publicar eventos de domínio (`UserCreatedEvent`, `UserDeactivatedEvent`, `UserDeletedEvent`) no use case via `ApplicationEventPublisher`
- [ ] **T2.7** — Testes unitários dos use cases (mocks)
- [ ] **T2.8** — Testes de integração do controller (MockMvc / WebTestClient)

### Fase 3 — Domínio de E-mail + Retry

- [ ] **T3.1** — Criar entidades de domínio: `EmailRequest`, `EmailStatus` (enum), `RetryControl`
- [ ] **T3.2** — Criar portas: `EmailRequestRepository`, `RetryControlRepository`, `EmailSender` (interface), `TemplateRenderer` (interface)
- [ ] **T3.3** — Criar use cases: `SendEmailUseCase`, `ProcessRetryUseCase`
- [ ] **T3.4** — Criar adaptador out `StubEmailSender` (log) implementando `EmailSender`
- [ ] **T3.5** — Criar adaptador out `InMemoryTemplateRenderer` (stub com strings internas) implementando `TemplateRenderer`
- [ ] **T3.6** — Criar adaptador out `EmailRequestJpaRepository`, `RetryControlJpaRepository` + JPA entities + mappers
- [ ] **T3.7** — Criar listener de eventos (`@TransactionalEventListener`) que chama `SendEmailUseCase`
- [ ] **T3.8** — Criar `@Scheduled` job para retry de e-mails `PENDING`/`RETRYING`
- [ ] **T3.9** — Testes unitários dos use cases de e-mail
- [ ] **T3.10** — Testes de integração do fluxo completo (criar user → evento → email_request persistido)

---

## 6. Arquivos Criados/Alterados por Tarefa

| Tarefa | Arquivos |
|--------|----------|
| **T1.1** | `build.gradle.kts`, `settings.gradle.kts`, `gradle/wrapper/*`, `src/main/kotlin/.../Application.kt` |
| **T1.2** | Criação dos pacotes: `domain/model`, `domain/port`, `domain/event`, `application/usecase`, `adapter/in/web`, `adapter/out/persistence`, `adapter/out/email`, `adapter/out/template`, `config` |
| **T1.3** | `docker-compose.yml` |
| **T1.4** | `Dockerfile` |
| **T1.5** | `src/main/resources/application.yml`, `application-local.yml` |
| **T1.6** | `src/main/resources/db/migration/V1__create_initial_schema.sql` |
| **T1.7** | Alteração em `build.gradle.kts` (dep actuator), `application.yml` (config actuator) |
| **T2.1** | `domain/model/User.kt`, `domain/model/UserId.kt`, `domain/model/UserStatus.kt` |
| **T2.2** | `domain/port/UserRepository.kt` |
| **T2.3** | `application/usecase/CreateUserUseCase.kt`, `DeactivateUserUseCase.kt`, `DeleteUserUseCase.kt`, `GetUserUseCase.kt`, `ListUsersUseCase.kt` |
| **T2.4** | `adapter/out/persistence/UserJpaEntity.kt`, `UserJpaRepository.kt`, `UserPersistenceAdapter.kt`, `UserMapper.kt` |
| **T2.5** | `adapter/in/web/UserController.kt`, `dto/CreateUserRequest.kt`, `dto/UserResponse.kt` |
| **T2.6** | `domain/event/UserCreatedEvent.kt`, `UserDeactivatedEvent.kt`, `UserDeletedEvent.kt`; alteração nos use cases de T2.3 |
| **T2.7** | `src/test/kotlin/.../application/usecase/*UseCaseTest.kt` |
| **T2.8** | `src/test/kotlin/.../adapter/in/web/UserControllerIntegrationTest.kt` |
| **T3.1** | `domain/model/EmailRequest.kt`, `domain/model/EmailStatus.kt`, `domain/model/RetryControl.kt` |
| **T3.2** | `domain/port/EmailRequestRepository.kt`, `domain/port/RetryControlRepository.kt`, `domain/port/EmailSender.kt`, `domain/port/TemplateRenderer.kt` |
| **T3.3** | `application/usecase/SendEmailUseCase.kt`, `ProcessRetryUseCase.kt` |
| **T3.4** | `adapter/out/email/StubEmailSender.kt` |
| **T3.5** | `adapter/out/template/InMemoryTemplateRenderer.kt` |
| **T3.6** | `adapter/out/persistence/EmailRequestJpaEntity.kt`, `EmailRequestJpaRepository.kt`, `EmailRequestPersistenceAdapter.kt`, `RetryControlJpaEntity.kt`, `RetryControlJpaRepository.kt`, `RetryControlPersistenceAdapter.kt` |
| **T3.7** | `adapter/in/event/UserEventListener.kt` |
| **T3.8** | `config/SchedulingConfig.kt`, `adapter/in/scheduler/RetryScheduler.kt` |
| **T3.9** | `src/test/kotlin/.../application/usecase/SendEmailUseCaseTest.kt`, `ProcessRetryUseCaseTest.kt` |
| **T3.10** | `src/test/kotlin/.../integration/EmailFlowIntegrationTest.kt` |

---

## 7. Critérios de Aceite por Tarefa

| Tarefa | Critério |
|--------|----------|
| **T1.1–T1.5** | `docker-compose up` sobe app + postgres; app inicia sem erros |
| **T1.6** | Flyway aplica migration; tabelas existem no banco |
| **T1.7** | `GET /actuator/health` retorna `200 { "status": "UP" }` |
| **T2.1–T2.5** | CRUD completo via REST: POST cria user, GET lista/busca, PATCH desativa, DELETE remove (soft/hard) |
| **T2.6** | Ao criar/desativar/deletar user, evento é publicado (verificável via log ou teste) |
| **T2.7** | Testes unitários passam; cobertura dos use cases ≥ 90% |
| **T2.8** | Testes de integração do controller passam com banco real (Testcontainers) |
| **T3.1–T3.6** | `email_request` é persistido com status `PENDING` ao receber evento |
| **T3.7** | Listener consome evento e invoca `SendEmailUseCase`; stub loga o envio; status muda para `SENT` |
| **T3.8** | Job de retry reprocessa registros `RETRYING`; após max tentativas, status muda para `FAILED` |
| **T3.9–T3.10** | Todos os testes passam; fluxo user → evento → email_request → status funciona end-to-end |

---

## 8. Estratégia de Testes

| Camada | Tipo | Ferramenta | Escopo |
|--------|------|------------|--------|
| `domain` + `application` | **Unitário** | JUnit 5 + MockK | Use cases isolados com mocks das portas |
| `adapter.in.web` | **Integração** | MockMvc ou WebTestClient + PostgreSQL local (`application-test.yml`) | Controllers com banco real usando o profile `test` |
| `adapter.out.persistence` | **Integração** | `@DataJpaTest` + PostgreSQL local (`application-test.yml`) | Repositórios JPA com banco real usando o profile `test` |
| **Fluxo completo** | **E2E (in-process)** | `@SpringBootTest` + PostgreSQL local (`application-test.yml`) | User criado → evento → `email_request` persistido com status correto |

> **Convenção**: testes unitários no mesmo pacote da classe; testes de integração junto aos adapters (ex.: `com.emailnotification.adapter.in.web`), usando o profile `test` com PostgreSQL configurado em `application-test.yml`.

---

## 9. Riscos + Mitigação

| # | Risco | Probabilidade | Impacto | Mitigação |
|---|-------|:---:|:---:|-----------|
| R1 | Evento publicado mas listener falha → e-mail nunca enviado | Média | Alto | `@TransactionalEventListener(AFTER_COMMIT)` + fallback: job de retry captura `PENDING` não processados após X minutos |
| R2 | Retry infinito em e-mails permanentemente inválidos | Média | Médio | `maxRetries` configurável em `retry_control`; após limite → `FAILED` |
| R3 | Stub esquecido em produção | Baixa | Alto | Profile-based: `StubEmailSender` ativado apenas em `local`/`test`; profile `prod` exige implementação real (fail-fast no startup) |
| R4 | Migration Flyway quebra em evolução futura | Baixa | Alto | Migrations imutáveis; alterações sempre em nova versão; testes de integração validam schema |
| R5 | Acoplamento temporal: evento síncrono trava request | Média | Médio | `@Async` no listener; se performance for problema, migrar para mensageria |

---

## 10. Plano de Rollback

| Cenário | Ação |
|---------|------|
| Migration Flyway com erro | Criar migration reversa (`V*__rollback_*.sql`); nunca alterar migration já aplicada |
| Feature de e-mail instável | Feature flag ou desabilitar listener via property (`app.email.enabled=false`) |
| Erro crítico no retry job | Desabilitar scheduling via property (`app.retry.enabled=false`); corrigir e re-deploy |
| Revert geral | Git revert do commit; re-deploy da versão anterior; Flyway rollback migration se necessário |

---

## 11. Observabilidade

| Aspecto | Implementação |
|---------|---------------|
| **Logs** | SLF4J + Logback; logs estruturados (JSON em prod). Log em: criação de user, publicação de evento, tentativa de envio, sucesso, falha, retry. |
| **Métricas** (futuro) | Micrometer + Actuator: contadores de e-mails enviados/falhados, histograma de latência de envio, gauge de fila de retry. |
| **Tracing** (futuro) | Correlation ID (`X-Correlation-Id`) propagado do request ao evento ao e-mail; preparar header no controller. |
| **Health check** | Actuator `/health` com indicadores de DB e disco. |

---

## 12. Próximo Passo Recomendado

> **Executar T1.1** — Inicializar o projeto Spring Boot com `build.gradle.kts` (Kotlin DSL), configurando as dependências base: Spring Web, Spring Data JPA, PostgreSQL driver, Flyway, Spring Boot Actuator, e dependências de teste (JUnit 5, MockK). Isso desbloqueia todas as tarefas subsequentes.


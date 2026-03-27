# Email Notification Backend

Backend em Kotlin + Spring Boot para gerenciar usuarios e disparar notificacoes de e-mail baseadas em eventos de dominio.

> Estado atual: **Fase 3 concluida** — CRUD de usuarios + fluxo completo de e-mail/retry implementado, com **51 testes passando**.

## Objetivo

- CRUD de usuarios (Fase 2)
- Disparo de e-mails em eventos de dominio:
  - `UserCreated`
  - `UserDeactivated`
  - `UserDeleted`
- Persistencia de historico de envio, status e tentativas de retry
- Retry assíncrono com scheduler para falhas transientes

## Stack

| Tecnologia | Versao | Finalidade |
|-----------|--------|------------|
| Kotlin | 1.9.25 | Linguagem principal |
| Java (JVM) | 21 | Target runtime |
| Spring Boot | 3.4.3 | Framework web + DI |
| Gradle (Kotlin DSL) | 8.12 | Build tool |
| PostgreSQL | 16 (Alpine) | Banco de dados |
| Flyway | 10.20.1 | Migrations de schema |
| Docker / Docker Compose | — | Orquestracao local |
| Testcontainers | 1.20.5 | Banco real em testes |
| MockK | 1.13.13 | Mocking para Kotlin |
| JUnit 5 | 5.11.4 | Framework de testes |

## Arquitetura

- Clean Architecture + Ports and Adapters (Hexagonal)
- Estrutura principal em `domain`, `application`, `adapter`, `config`
- Eventos de dominio para desacoplar fluxo de usuario e envio de e-mail
- `domain` e `application` sem dependencias de framework

## Como rodar

### 1) Subir app + banco com Docker

```bash
docker compose up -d
docker compose ps
```

### 2) Validar healthcheck

```bash
curl http://localhost:8081/actuator/health
```

### 3) Desenvolvimento local (sem Docker para a app)

```bash
# subir apenas o banco
docker compose up -d postgres

# rodar a app localmente (porta 8080)
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

### 4) Executar testes

```bash
# requer apenas Docker ativo (Testcontainers sobe PostgreSQL automaticamente)
./gradlew test
```

### 5) Derrubar ambiente

```bash
docker compose down
```

## Endpoints disponiveis

### Infraestrutura

| Metodo | Path | Descricao |
|--------|------|-----------|
| GET | `/actuator/health` | Health check (DB, disco, ping) |
| GET | `/actuator/info` | Informacoes da aplicacao |

**Docker app**: `http://localhost:8081` | **App local**: `http://localhost:8080`

### Users

| Metodo | Path | Descricao | HTTP |
|--------|------|-----------|------|
| POST | `/users` | Cria usuario (`ACTIVE`) | 201 |
| GET | `/users` | Lista usuarios nao-deletados | 200 |
| GET | `/users/{publicId}` | Busca usuario por UUID | 200 / 404 |
| PATCH | `/users/{publicId}/deactivate` | Desativa usuario (`ACTIVE→INACTIVE`) | 200 / 404 / 422 |
| DELETE | `/users/{publicId}` | Soft-delete do usuario | 200 / 404 / 422 |

> O `publicId` e um UUID exposto na API. O `id` interno (BIGINT) nunca e retornado.

## Fluxo de e-mail (Fase 3)

- `UserEventListener` consome eventos `UserCreated`, `UserDeactivated`, `UserDeleted`
- `SendEmailUseCase` persiste `email_request` (PENDING), tenta envio e transiciona para `SENT`, `RETRYING` ou `FAILED`
- `RetryScheduler` reprocessa `RETRYING` via `ProcessRetryUseCase`
- `email_status` guarda audit trail completo de transicoes
- `StubEmailSender` e `InMemoryTemplateRenderer` permitem execucao local sem dependencias externas

## Qualidade e testes

- **Total atual**: 51 testes (0 falhas)
- Unitarios de usuario + e-mail (`domain`/`application`)
- Integracao de API (`UserControllerIntegrationTest`)
- Integracao E2E do fluxo de e-mail (`EmailFlowIntegrationTest`)

## Documentacao do projeto

- Contexto arquitetural: `docs/ARCHITETURE_CONTEXT.md`
- Plano tecnico: `docs/plan-emailNotificationBackend.prompt.md`
- Diario de desenvolvimento (atualizado ao longo das entregas): `docs/DEVELOPMENT_LOG.md`

## Proximos passos

- Fase 4: observabilidade e producao (metricas, logs estruturados, tracing e hardening).

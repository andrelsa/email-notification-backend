# Email Notification Backend

Backend em Kotlin + Spring Boot para gerenciar usuarios e disparar notificacoes de e-mail baseadas em eventos de dominio.

> Estado atual: **Fase 2 concluida** — CRUD de usuarios, Clean Architecture completa, eventos de dominio, 37 testes passando.

## Objetivo

- CRUD de usuarios (implementado na Fase 2)
- Disparo de e-mails em eventos de dominio:
  - `UserCreated`
  - `UserDeactivated`
  - `UserDeleted`
- Persistencia de historico de envio, status e tentativas de retry

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
# requer banco email_notification_test rodando localmente
# crie se necessario: docker exec email-notification-db psql -U postgres -c "CREATE DATABASE email_notification_test;"
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

## Colecao Postman

Arquivos em `docs/postman/`:

| Arquivo | Descricao |
|---------|-----------|
| `email-notification-backend.postman_collection.json` | Colecao completa com todos os endpoints, exemplos e scripts de teste automatico |
| `email-notification-backend.postman_environment.json` | Environment `Email Notification Backend - Local` com `base_url=http://localhost:8081` |

**Como importar**: Postman → Import → selecione os dois arquivos → escolha o environment → execute `Create User` (o `publicId` e capturado automaticamente).

## Banco de dados local

- Host: `localhost` | Port: `5432`
- Database: `email_notification` (app) / `email_notification_test` (testes)
- Username / Password: `postgres` / `postgres`

Se voce alterou credenciais recentemente, recrie o volume:

```bash
docker compose down -v && docker compose up -d
```

## Rodar app Docker + app local ao mesmo tempo

- Docker publica a app em `8081` (`8081:8080`)
- App local continua em `8080`
- Banco permanece em `5432`

## Estrutura do repositorio

```text
email-notification-backend/
|- src/main/kotlin/com/emailnotification/
|  |- Application.kt
|  |- domain/{model,port,event,exception}/
|  |- application/usecase/
|  |- adapter/{in/web, out/persistence}/
|  `- config/
|- src/main/resources/
|  |- application.yml
|  |- application-local.yml
|  `- db/migration/V1__create_initial_schema.sql
|- src/test/kotlin/com/emailnotification/
|  |- domain/model/UserTest.kt
|  |- application/usecase/
|  `- adapter/in/web/UserControllerIntegrationTest.kt
|- docs/
|  |- ARCHITETURE_CONTEXT.md
|  |- DEVELOPMENT_LOG.md
|  |- plan-emailNotificationBackend.prompt.md
|  `- postman/
|- docker-compose.yml
|- Dockerfile
`- build.gradle.kts
```

## Documentacao do projeto

- Contexto arquitetural: `docs/ARCHITETURE_CONTEXT.md`
- Plano tecnico: `docs/plan-emailNotificationBackend.prompt.md`
- Diario de desenvolvimento (atualizado ao longo das entregas): `docs/DEVELOPMENT_LOG.md`

## Proximos passos

- Fase 3: dominio de e-mail (`EmailRequest`, `EmailStatus`, `RetryControl`), listener de eventos, job de retry e stubs de envio/template.

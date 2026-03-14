# Email Notification Backend

Backend em Kotlin + Spring Boot para gerenciar usuarios e disparar notificacoes de e-mail baseadas em eventos de dominio.

> Estado atual: **Fase 1 concluida** (bootstrap, infra, banco, migrations e actuator).

## Objetivo

- CRUD de usuarios (fase seguinte)
- Disparo de e-mails em eventos de dominio:
  - `UserCreated`
  - `UserDeactivated`
  - `UserDeleted`
- Persistencia de historico de envio, status e tentativas de retry

## Stack

- Kotlin 1.9.25
- Java 21
- Spring Boot 3.4.3
- Gradle 8.12 (Kotlin DSL)
- PostgreSQL 16 (Docker)
- Flyway
- Docker / Docker Compose

## Arquitetura

- Clean Architecture + Ports and Adapters (Hexagonal)
- Estrutura principal em `domain`, `application`, `adapter`, `config`
- Eventos de dominio para desacoplar fluxo de usuario e envio de e-mail

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

### 3) Derrubar ambiente

```bash
docker compose down
```

## Banco de dados local

- Host: `localhost`
- Port: `5432`
- Database: `email_notification`
- Username: `postgres`
- Password: `postgres`

> Observacao: o projeto foi padronizado para `postgres/postgres` no ambiente local.

Se voce alterou credenciais recentemente, recrie o volume:

```bash
docker compose down -v
docker compose up -d
```

## Configuracao da aplicacao

Arquivo base: `src/main/resources/application.yml`

- Datasource por variavel de ambiente com fallback local
- JPA com `ddl-auto: validate`
- Flyway habilitado
- Actuator expondo `health` e `info`

Variaveis principais:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_PROFILES_ACTIVE`

## Endpoints disponiveis (Fase 1)

- Docker app: `GET http://localhost:8081/actuator/health`
- Docker app: `GET http://localhost:8081/actuator/info`
- App local (`bootRun`): `GET http://localhost:8080/actuator/health`

## Rodar app Docker + app local ao mesmo tempo

- Docker publica a app em `8081` (`8081:8080`)
- App local continua em `8080`
- Banco permanece em `5432`

## Estrutura do repositorio

```text
email-notification-backend/
|- src/main/kotlin/com/emailnotification/
|  |- domain/
|  |- application/
|  |- adapter/
|  `- config/
|- src/main/resources/
|  |- application.yml
|  `- db/migration/V1__create_initial_schema.sql
|- docs/
|  |- ARCHITECTURE_CONTEXT.md
|  |- DEVELOPMENT_LOG.md
|  `- plan-emailNotificationBackend.prompt.md
|- docker-compose.yml
|- Dockerfile
`- build.gradle.kts
```

## Documentacao do projeto

- Contexto arquitetural: `docs/ARCHITECTURE_CONTEXT.md`
- Plano tecnico: `docs/plan-emailNotificationBackend.prompt.md`
- Diario de desenvolvimento (atualizado ao longo das entregas): `docs/DEVELOPMENT_LOG.md`

## Proximos passos

- Implementar Fase 2: dominio de usuario, CRUD REST e publicacao de eventos.

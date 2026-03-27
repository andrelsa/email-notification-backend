# Email Notification Backend — Diário de Desenvolvimento

> Documento atualizado a cada merge na `main`. Serve como referência para compreender o estado atual do projeto e prosseguir com o desenvolvimento.

---

## Índice

- [Visão Geral](#visão-geral)
- [Stack Tecnológica](#stack-tecnológica)
- [Fase 1 — Fundação (Bootstrap & Infraestrutura)](#fase-1--fundação-bootstrap--infraestrutura)
- [Fase 2 — Domínio de Usuário + Eventos](#fase-2--domínio-de-usuário--eventos)
- [Fase 3 — Domínio de E-mail + Retry](#fase-3--domínio-de-e-mail--retry)
- [Como Rodar o Projeto](#como-rodar-o-projeto)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Banco de Dados](#banco-de-dados)
- [Configurações por Ambiente](#configurações-por-ambiente)
- [Endpoints Disponíveis](#endpoints-disponíveis)
- [Coleção Postman](#coleção-postman)
- [Próximas Fases](#próximas-fases)
- [Histórico de Merges](#histórico-de-merges)

---

## Visão Geral

Serviço de notificações por e-mail desacoplado. Gerencia usuários (CRUD) e dispara e-mails em resposta a eventos de domínio (`UserCreated`, `UserDeactivated`, `UserDeleted`), com persistência de histórico de envio, status e retries.

Arquitetura baseada em **Clean Architecture** com **Portas e Adaptadores (Hexagonal)**, preparada para integração futura com template-service externo.

---

## Stack Tecnológica

| Tecnologia | Versão | Finalidade |
|-----------|--------|------------|
| Kotlin | 1.9.25 | Linguagem principal |
| Java (JVM) | 21 | Target runtime |
| Spring Boot | 3.4.3 | Framework web + DI |
| Gradle (Kotlin DSL) | 8.12 | Build tool |
| PostgreSQL | 16 (Alpine) | Banco de dados |
| Flyway | 10.20.1 | Migrations de schema |
| Docker / Docker Compose | — | Orquestração local |
| Testcontainers | 1.20.5 | Banco real em testes |
| MockK | 1.13.13 | Mocking para Kotlin |
| JUnit 5 | 5.11.4 | Framework de testes |

---

## Fase 1 — Fundação (Bootstrap & Infraestrutura)

> **Branch**: `feature/phase-1-bootstrap`  
> **Status**: ✅ Concluída e validada

### O que foi feito

A Fase 1 estabeleceu toda a infraestrutura necessária para o desenvolvimento do projeto. Nenhuma lógica de negócio foi implementada — apenas o esqueleto técnico.

### T1.1 — Inicialização do projeto Spring Boot

Criação do projeto com Gradle Kotlin DSL (`build.gradle.kts`), configurando:

- **Plugins**: Spring Boot 3.4.3, Spring Dependency Management, Kotlin JVM, Kotlin Spring (open classes), Kotlin JPA (no-arg constructors)
- **Dependências de produção**: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-actuator`, `spring-boot-starter-validation`, Jackson Kotlin, Kotlin Reflect, PostgreSQL driver, Flyway Core + PostgreSQL
- **Dependências de teste**: `spring-boot-starter-test`, Testcontainers (JUnit Jupiter + PostgreSQL), MockK
- **JVM target**: Java 21 com strict null-safety (`-Xjsr305=strict`)
- **Entry point**: `src/main/kotlin/com/emailnotification/Application.kt`

### T1.2 — Estrutura de pacotes (Clean Architecture)

Organização em 4 camadas dentro de um único módulo Gradle:

```
src/main/kotlin/com/emailnotification/
├── Application.kt                    # Entry point Spring Boot
├── domain/
│   ├── model/                        # Entidades e Value Objects do domínio
│   ├── port/                         # Interfaces (portas) — contratos do domínio
│   └── event/                        # Eventos de domínio
├── application/
│   └── usecase/                      # Casos de uso (orquestração)
├── adapter/
│   ├── in/
│   │   ├── web/                      # Controllers REST (entrada HTTP)
│   │   ├── event/                    # Listeners de eventos Spring
│   │   └── scheduler/               # Jobs agendados (@Scheduled)
│   └── out/
│       ├── persistence/              # JPA entities, repositories, mappers
│       ├── email/                    # Implementação de envio de e-mail
│       └── template/                 # Implementação de renderização de template
└── config/                           # Configurações Spring (beans, async, etc.)
```

**Regra de dependência**: `domain` → `application` → `adapter`. O domínio não conhece Spring nem JPA.

### T1.3 — Docker Compose

Arquivo `docker-compose.yml` com dois serviços:

| Serviço | Imagem | Porta | Detalhes |
|---------|--------|-------|----------|
| `postgres` | `postgres:16-alpine` | `5432:5432` | Health check com `pg_isready`, credenciais padrão `postgres/postgres`, volume persistente `pgdata` |
| `app` | Build local (`Dockerfile`) | `8081:8080` | Profile `local`, conecta ao postgres via service name, `depends_on` com `condition: service_healthy` |

A app **só inicia após o banco estar saudável**, evitando erros de conexão no startup.

### T1.4 — Dockerfile multi-stage

Build otimizado em 2 estágios:

1. **Build stage** (`eclipse-temurin:21-jdk-alpine`): copia o Gradle wrapper, baixa dependências primeiro (cache de layers), depois copia o source e gera o `bootJar`
2. **Runtime stage** (`eclipse-temurin:21-jre-alpine`): imagem mínima, usuário não-root (`appuser`), apenas o JAR final

Isso garante imagens menores e mais seguras em produção.

**Ajuste aplicado após revisão:** etapa de dependências no Dockerfile agora roda em modo **fail-fast** (sem `|| true`) para não mascarar falhas de resolução.

### T1.5 — Configurações por ambiente

Três arquivos de configuração:

**`application.yml`** (base — todos os ambientes):
- Datasource configurado via variáveis de ambiente com fallback para valores locais
- Fallback de credenciais padronizado para `postgres/postgres` (alinhado com outros projetos locais)
- JPA com `ddl-auto: validate` (Flyway gerencia o schema, Hibernate apenas valida)
- `open-in-view: false` (evita lazy loading acidental em controllers)
- Flyway habilitado apontando para `classpath:db/migration`
- Actuator expondo endpoints `health` e `info` com `show-details: never` (hardening padrão)

**`application-local.yml`** (profile `local`):
- `show-sql: true` para debug
- Logging DEBUG para `com.emailnotification`, `org.hibernate.SQL` e bind parameters
- `management.endpoint.health.show-details: always` para troubleshooting local

**`application-test.yml`** (profile `test`):
- Datasource apontando por padrão para um PostgreSQL local (com alternativa comentada usando Testcontainers via `jdbc:tc:postgresql:16-alpine:///`)
- Flyway habilitado para validar migrations em testes
- `management.endpoint.health.show-details: always` para facilitar diagnóstico em testes

### T1.6 — Migration Flyway V1

Arquivo: `V1__create_initial_schema.sql`

Inclui `CREATE EXTENSION IF NOT EXISTS pgcrypto;` para garantir suporte a `gen_random_uuid()` em ambientes novos (Docker/Testcontainers), evitando falha de startup no Flyway.

Cria 4 tabelas conforme definido no `ARCHITETURE_CONTEXT.md`:

#### `users`
| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | `BIGSERIAL PK` | ID interno (nunca exposto) |
| `public_id` | `UUID UNIQUE` | Identificador público (usado na API) |
| `name` | `VARCHAR(255)` | Nome do usuário |
| `email` | `VARCHAR(255) UNIQUE` | E-mail do usuário |
| `status` | `VARCHAR(20)` | `ACTIVE`, `INACTIVE`, `DELETED` |
| `created_at` / `updated_at` | `TIMESTAMP` | Auditoria temporal |

#### `email_request`
| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | `BIGSERIAL PK` | ID interno |
| `public_id` | `UUID UNIQUE` | Identificador público |
| `user_id` | `BIGINT FK → users` | Usuário associado |
| `recipient_email` | `VARCHAR(255)` | Destinatário |
| `subject` / `body` | `VARCHAR(500)` / `TEXT` | Conteúdo do e-mail |
| `event_type` | `VARCHAR(50)` | `USER_CREATED`, `USER_DEACTIVATED`, `USER_DELETED` |
| `status` | `VARCHAR(20)` | `PENDING`, `RETRYING`, `SENT`, `FAILED` |

#### `email_status` (audit trail)
| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | `BIGSERIAL PK` | ID interno |
| `email_request_id` | `BIGINT FK → email_request` | Request associado |
| `previous_status` / `new_status` | `VARCHAR(20)` | Transição de status |
| `description` | `TEXT` | Descrição/motivo da transição |
| `occurred_at` | `TIMESTAMP` | Momento da transição |

#### `retry_control`
| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | `BIGSERIAL PK` | ID interno |
| `email_request_id` | `BIGINT FK UNIQUE → email_request` | Relação 1:1 |
| `attempt_count` / `max_attempts` | `INT` | Controle de tentativas (default max: 3) |
| `last_attempt_at` / `next_attempt_at` | `TIMESTAMP` | Agendamento de retry |
| `last_error_message` | `TEXT` | Último erro registrado |

Todas as tabelas possuem **índices** nos campos mais consultados (public_id, status, FKs, next_attempt_at).

### T1.7 — Spring Boot Actuator

Dependência `spring-boot-starter-actuator` configurada com:
- **Endpoints expostos**: `/actuator/health`, `/actuator/info`
- **Health details (base)**: `show-details: never`
- **Health details (local/test)**: `show-details: always`

### T1.8 — Ajustes de robustez e versionamento (pós-revisão)

- `gradle/wrapper/gradle-wrapper.jar` versionado no repositório (wrapper completo)
- `.gitignore` ajustado para manter o wrapper JAR versionado
- `.gitignore` ajustado para permitir `src/main/resources/application-local.yml`
- `application-local.yml` versionado para suportar `SPRING_PROFILES_ACTIVE=local` no Docker Compose
- `gradle/wrapper/gradle-wrapper.properties` com `networkTimeout=60000` para reduzir falhas transitórias de download

Resposta validada:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "PostgreSQL" } },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

### Validação da Fase 1

| Critério | Resultado |
|----------|-----------|
| `./gradlew compileKotlin` | ✅ BUILD SUCCESSFUL |
| `./gradlew bootJar` | ✅ BUILD SUCCESSFUL |
| `docker compose up -d` sobe app + postgres | ✅ Ambos containers UP |
| Flyway aplica migration V1 | ✅ 4 tabelas + `flyway_schema_history` criadas |
| `GET /actuator/health` retorna 200 | ✅ `{"status":"UP"}` com DB conectado |

---

## Fase 2 — Domínio de Usuário + Eventos

> **Branch**: `feature/phase-2-user-events`  
> **Status**: ✅ Concluída e validada

### O que foi feito

A Fase 2 implementou o domínio de usuário completo — da camada de domínio até os endpoints REST — seguindo estritamente a Clean Architecture. Nenhuma dependência de infraestrutura existe nas camadas `domain` e `application`.

### T2.1 — Entidade de domínio `User` e value objects

Arquivos: `domain/model/User.kt`, `UserId.kt`, `UserStatus.kt`

- **`UserId`**: inline value class wrapping `java.util.UUID` — segurança de tipo sem overhead de boxing
- **`UserStatus`**: enum `ACTIVE | INACTIVE | DELETED`
- **`User`**: data class pura (sem anotações Spring/JPA), com:
  - `id: Long?` (nullable — null antes da persistência)
  - `publicId: UserId` (UUID gerado na criação)
  - `deactivate()` — valida que status é `ACTIVE`, lança `IllegalArgumentException` caso contrário
  - `delete()` — valida que status não é `DELETED`, lança `IllegalArgumentException` caso contrário
  - `isActive: Boolean` — computed property

**Regra**: o domínio não conhece Spring, JPA nem qualquer framework.

### T2.2 — Porta de saída `UserRepository`

Arquivo: `domain/port/UserRepository.kt`

Interface definida no domínio, implementada pela camada de infraestrutura:

```kotlin
interface UserRepository {
    fun save(user: User): User
    fun findByPublicId(publicId: UserId): User?
    fun findAll(): List<User>           // exclui DELETED
    fun existsByEmail(email: String): Boolean
}
```

### T2.3 — Eventos de domínio e use cases

**Eventos** (`domain/event/`):
- `UserCreatedEvent(user: User)`
- `UserDeactivatedEvent(user: User)`
- `UserDeletedEvent(user: User)`

**Exceções** (`domain/exception/`):
- `UserNotFoundException(publicId: UserId)`
- `EmailAlreadyExistsException(email: String)`

**Use cases** (`application/usecase/`):

| Use case | Responsabilidade | Transação |
|----------|-----------------|-----------|
| `CreateUserUseCase` | Valida e-mail único, salva, publica `UserCreatedEvent` | `@Transactional` |
| `GetUserUseCase` | Busca por `publicId`, lança 404 se não encontrado | `@Transactional(readOnly=true)` |
| `ListUsersUseCase` | Retorna todos não-deletados | `@Transactional(readOnly=true)` |
| `DeactivateUserUseCase` | Busca, chama `user.deactivate()`, salva, publica evento | `@Transactional` |
| `DeleteUserUseCase` | Busca, chama `user.delete()`, salva, publica evento | `@Transactional` |

### T2.4 — Adaptador de persistência JPA

Arquivos: `adapter/out/persistence/`

- **`UserJpaEntity`**: `@Entity` mapeada para tabela `users`; `publicId` e `createdAt` marcados `updatable=false`
- **`UserJpaRepository`**: Spring Data com `findByPublicId`, `findAllByStatusNot`, `existsByEmail`
- **`UserMapper`**: converte `UserJpaEntity ↔ User` (domínio); modelos completamente desacoplados
- **`UserPersistenceAdapter`**: implementa `UserRepository`; `findAll()` exclui `DELETED`

### T2.5 — Adaptador web REST

Arquivos: `adapter/in/web/`

| Endpoint | Método | Descrição |
|----------|--------|-----------|
| `POST /users` | `createUser` | Cria usuário, retorna 201 |
| `GET /users` | `listUsers` | Lista não-deletados, retorna 200 |
| `GET /users/{publicId}` | `getUser` | Busca por UUID, retorna 200 ou 404 |
| `PATCH /users/{publicId}/deactivate` | `deactivateUser` | ACTIVE→INACTIVE, retorna 200 |
| `DELETE /users/{publicId}` | `deleteUser` | Soft-delete, retorna 200 |

**DTOs**:
- `CreateUserRequest`: `@NotBlank`, `@Email`, `@Size(max=255)` nos campos
- `UserResponse`: expõe apenas `publicId` (UUID string) — `id` interno nunca é exposto
- `ErrorResponse`: `status`, `error`, `message`, `timestamp`

**`GlobalExceptionHandler`** (@RestControllerAdvice):

| Exceção | HTTP |
|---------|------|
| `UserNotFoundException` | 404 Not Found |
| `EmailAlreadyExistsException` | 409 Conflict |
| `IllegalArgumentException` | 422 Unprocessable Entity |
| `MethodArgumentNotValidException` | 400 Bad Request |

### T2.6 — Configuração Spring (`AppConfig`)

- Bean `ApplicationEventPublisher` injetado automaticamente pelo Spring
- Nenhuma configuração adicional necessária para eventos síncronos

### T2.7 — Testes unitários

22 testes no total cobrindo:

| Classe de teste | Casos |
|-----------------|-------|
| `UserTest` | 9 — máquina de estados `deactivate`/`delete`, `isActive`, guards |
| `CreateUserUseCaseTest` | 2 — happy path + `EmailAlreadyExistsException` |
| `GetUserUseCaseTest` | 2 — encontrado + `UserNotFoundException` |
| `ListUsersUseCaseTest` | 2 — lista com dados + lista vazia |
| `DeactivateUserUseCaseTest` | 3 — sucesso + não encontrado + violação de regra |
| `DeleteUserUseCaseTest` | 4 — ACTIVE/INACTIVE deletados + não encontrado + já deletado |

Tecnologias: **MockK** para mocks, **JUnit 5**, `@ExtendWith(MockKExtension::class)`.

### T2.8 — Testes de integração

15 testes no total cobrindo todos os endpoints:

| Cenário | Endpoint | HTTP esperado |
|---------|----------|---------------|
| Criar usuário | `POST /users` | 201 |
| Criar (body inválido — name blank) | `POST /users` | 400 |
| Criar (e-mail inválido) | `POST /users` | 400 |
| Criar (e-mail duplicado) | `POST /users` | 409 |
| Listar usuários | `GET /users` | 200 |
| Listar (lista vazia) | `GET /users` | 200 |
| Listar (sem DELETED) | `GET /users` | 200 |
| Buscar por ID | `GET /users/{id}` | 200 |
| Buscar por ID (não encontrado) | `GET /users/{id}` | 404 |
| Desativar | `PATCH /users/{id}/deactivate` | 200 |
| Desativar (não encontrado) | `PATCH /users/{id}/deactivate` | 404 |
| Desativar (já INACTIVE) | `PATCH /users/{id}/deactivate` | 422 |
| Deletar | `DELETE /users/{id}` | 200 |
| Deletar (não encontrado) | `DELETE /users/{id}` | 404 |
| Deletar (já DELETED) | `DELETE /users/{id}` | 422 |

**Configuração de testes**: Testcontainers (`jdbc:tc:postgresql:16-alpine:///email_notification_test`), sem necessidade de banco local dedicado.
> **Nota sobre Docker Engine + macOS**: o build configura `jvmArgs("-Dapi.version=1.44")` para compatibilidade com Docker Engine 27+ (API mínima 1.44) usando testcontainers-1.20.5.

### T2.9 — Documentação, Postman e README

- Coleção Postman v2.1 criada em `docs/postman/` com todos os 5 endpoints + health check
- Environment Postman configurado para `http://localhost:8081` (Docker)
- Script de test automático para capturar `publicId` após `POST /users`
- `DEVELOPMENT_LOG.md` atualizado com Fase 2 completa
- `README.md` atualizado refletindo Fase 2

### Validação da Fase 2

| Critério | Resultado |
|----------|-----------|
| `./gradlew compileKotlin` | ✅ BUILD SUCCESSFUL |
| `./gradlew test` (unit) | ✅ 22/22 testes passando |
| `./gradlew test` (integration) | ✅ 15/15 testes passando |
| `./gradlew clean build` | ✅ BUILD SUCCESSFUL (37 testes) |
| `POST /users` cria usuário | ✅ 201 com `publicId` UUID |
| `GET /users` lista não-deletados | ✅ 200 exclui DELETED |
| `PATCH /users/{id}/deactivate` | ✅ ACTIVE→INACTIVE com 200 |
| `DELETE /users/{id}` soft-delete | ✅ DELETED retornado com 200 |
| Eventos de domínio publicados | ✅ `UserCreated`, `UserDeactivated`, `UserDeleted` |

---

## Fase 3 — Domínio de E-mail + Retry

> **Branch**: `feature/phase-3-email-domain`  
> **Status**: ✅ Concluída e validada

### O que foi feito

A Fase 3 implementou o fluxo completo de notificações de e-mail, incluindo:
- persistência de `email_request`, `email_status` e `retry_control`
- envio assíncrono por eventos de domínio (`@Async + AFTER_COMMIT`)
- scheduler de retry com kill switch por configuração
- testes unitários dos casos de uso de e-mail e teste E2E do fluxo completo

### Entregas por etapa

| Etapa | Entrega | Resultado |
|------|---------|-----------|
| T3.1 | Entidades de domínio (`EmailRequest`, `EmailStatus`, `RetryControl`, `EmailStatusEntry`, `EmailRequestId`, `EmailEventType`) | ✅ |
| T3.2 | Portas (`EmailRequestRepository`, `RetryControlRepository`, `EmailSender`, `TemplateRenderer`) | ✅ |
| T3.3 | Use cases `SendEmailUseCase` e `ProcessRetryUseCase` | ✅ |
| T3.4 | `StubEmailSender` (perfil `!prod`) | ✅ |
| T3.5 | `InMemoryTemplateRenderer` | ✅ |
| T3.6 | Adaptadores JPA + mappers para domínio de e-mail | ✅ |
| T3.7 | `UserEventListener` (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`) | ✅ |
| T3.8 | `RetryScheduler` com `@Scheduled` e `app.email.retry.enabled` | ✅ |
| T3.9 | Testes unitários de e-mail (`SendEmailUseCaseTest`, `ProcessRetryUseCaseTest`) | ✅ |
| T3.10 | Teste E2E `EmailFlowIntegrationTest` | ✅ |

### Validação da Fase 3

| Critério | Resultado |
|----------|-----------|
| `./gradlew clean test` | ✅ BUILD SUCCESSFUL |
| Testes unitários de e-mail | ✅ Implementados e passando |
| Listener assíncrono pós-commit | ✅ Validado em integração |
| Scheduler de retry | ✅ Implementado com kill switch |
| Fluxo E2E user -> evento -> email_request | ✅ Validado por `EmailFlowIntegrationTest` |
| Total de testes da suite | ✅ 51 testes, 0 falhas |

---

## Como Rodar o Projeto

### Pré-requisitos
- Docker e Docker Compose instalados
- (Opcional) Java 21 + Gradle para desenvolvimento local

### Subir com Docker Compose
```bash
docker compose up -d
```

### Verificar se está rodando
```bash
# Status dos containers
docker compose ps

# App em Docker (porta publicada 8081)
curl http://localhost:8081/actuator/health

# App local (quando executada via bootRun)
curl http://localhost:8080/actuator/health
```

### Derrubar
```bash
docker compose down
```

### Desenvolvimento local (sem Docker para a app)
```bash
# 1. Subir apenas o banco
docker compose up -d postgres

# 2. Rodar a app localmente
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

### Executar testes
```bash
# Requer Docker rodando (Testcontainers sobe PostgreSQL automaticamente)
./gradlew test
```

---

## Estrutura do Projeto

```
email-notification-backend/
├── docs/
│   ├── ARCHITETURE_CONTEXT.md          # Contexto arquitetural do projeto
│   ├── plan-emailNotificationBackend.prompt.md  # Plano técnico completo
│   └── DEVELOPMENT_LOG.md             # Este documento
├── src/
│   ├── main/
│   │   ├── kotlin/com/emailnotification/
│   │   │   ├── Application.kt
│   │   │   ├── domain/{model,port,event}/
│   │   │   ├── application/usecase/
│   │   │   ├── adapter/{in,out}/
│   │   │   └── config/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       └── db/migration/V1__create_initial_schema.sql
│   └── test/
│       └── resources/application-test.yml
├── build.gradle.kts
├── settings.gradle.kts
├── docker-compose.yml
├── Dockerfile
├── gradlew / gradlew.bat
└── .gitignore
```

---

## Banco de Dados

### Diagrama de Relacionamentos

```
┌──────────┐       ┌───────────────┐       ┌──────────────┐
│  users   │──1:N──│ email_request │──1:N──│ email_status │
│          │       │               │       │ (audit trail)│
└──────────┘       └───────┬───────┘       └──────────────┘
                           │
                         1:1
                           │
                   ┌───────┴───────┐
                   │ retry_control │
                   └───────────────┘
```

### Status Machine (email_request)

```
    ┌─────────┐
    │ PENDING │
    └────┬────┘
         │
    ┌────┴────┐──── sucesso ───→ ┌──────┐
    │  envio  │                  │ SENT │
    └────┬────┘                  └──────┘
         │
      falha
         │
    ┌────┴─────┐
    │ RETRYING │──── max tentativas ───→ ┌────────┐
    └────┬─────┘                         │ FAILED │
         │                               └────────┘
         └──── sucesso ───→ ┌──────┐
                            │ SENT │
                            └──────┘
```

---

## Configurações por Ambiente

| Property | Base | Local | Test |
|----------|------|-------|------|
| Datasource URL | `${ENV}` / `localhost:5432` | (herda base) | Testcontainers (`jdbc:tc:...`) |
| Datasource Username | `${ENV}` / `postgres` | (herda base) | Testcontainers (`jdbc:tc:...`) |
| Datasource Password | `${ENV}` / `postgres` | (herda base) | Testcontainers (`jdbc:tc:...`) |
| JPA ddl-auto | `validate` | (herda) | `validate` |
| JPA show-sql | `false` | `true` | (herda) |
| Flyway | enabled | (herda) | enabled |
| Health details (`/actuator/health`) | `never` | `always` | `always` |
| Log level app | INFO | DEBUG | (herda) |
| Log Hibernate SQL | — | DEBUG + TRACE binds | — |

### Nota Operacional — Padronização de credenciais locais

- Para manter o padrão entre projetos, o ambiente local foi alinhado para `postgres/postgres` em `docker-compose.yml` e `application.yml`.
- Como o usuário do PostgreSQL é criado na inicialização do volume, após essa troca é recomendado recriar o volume local:

```bash
docker compose down -v
docker compose up -d
```

---

## Endpoints Disponíveis

### Infraestrutura

| Método | Path | Descrição | Status |
|--------|------|-----------|--------|
| GET | `/actuator/health` | Health check (DB, disco, ping) | ✅ Fase 1 |
| GET | `/actuator/info` | Informações da aplicação | ✅ Fase 1 |

### Users

| Método | Path | Descrição | HTTP | Status |
|--------|------|-----------|------|--------|
| POST | `/users` | Cria usuário (`ACTIVE`) | 201 | ✅ Fase 2 |
| GET | `/users` | Lista usuários não-deletados | 200 | ✅ Fase 2 |
| GET | `/users/{publicId}` | Busca usuário por UUID | 200 / 404 | ✅ Fase 2 |
| PATCH | `/users/{publicId}/deactivate` | Desativa usuário (`ACTIVE→INACTIVE`) | 200 / 404 / 422 | ✅ Fase 2 |
| DELETE | `/users/{publicId}` | Soft-delete do usuário | 200 / 404 / 422 | ✅ Fase 2 |

**Base URL**:
- Docker app: `http://localhost:8081`
- App local (`bootRun`): `http://localhost:8080`

---

## Coleção Postman

Arquivos em `docs/postman/`:

| Arquivo | Descrição |
|---------|-----------|
| `email-notification-backend.postman_collection.json` | Coleção completa com todos os endpoints, exemplos de request/response e scripts de teste |
| `email-notification-backend.postman_environment.json` | Environment `Email Notification Backend - Local` com `base_url=http://localhost:8081` |

### Como importar

1. Abra o Postman
2. **Import** → selecione `email-notification-backend.postman_collection.json`
3. **Import** → selecione `email-notification-backend.postman_environment.json`
4. Selecione o environment **Email Notification Backend - Local**
5. Execute `Create User` — o `publicId` é capturado automaticamente para os demais requests

### Fluxo de teste sugerido

```
1. Infrastructure / Health Check        → GET  /actuator/health        (200 UP)
2. Users / Create User                  → POST /users                   (201 + captura publicId)
3. Users / List Users                   → GET  /users                   (200 com [1 usuário])
4. Users / Get User by ID               → GET  /users/{{publicId}}      (200)
5. Users / Deactivate User              → PATCH /users/{{publicId}}/deactivate (200 INACTIVE)
6. Users / Deactivate User (novamente)  → PATCH /users/{{publicId}}/deactivate (422)
7. Users / Delete User                  → DELETE /users/{{publicId}}    (200 DELETED)
8. Users / Delete User (novamente)      → DELETE /users/{{publicId}}    (422)
9. Users / List Users                   → GET  /users                   (200 lista vazia — DELETED excluído)
```

---

## Próximas Fases

### Fase 4 — Observabilidade & Produção (Próxima)
- Métricas com Micrometer + Prometheus
- Logs estruturados (JSON)
- Tracing distribuído
- Hardening de segurança

---

## Histórico de Merges

| Data | Branch | Fase | Descrição |
|------|--------|------|-----------|
| 2026-03-07 | `feature/phase-1-bootstrap` | Fase 1 | Bootstrap do projeto: Spring Boot, Gradle, Docker Compose, Flyway, Actuator, estrutura Clean Architecture |
| 2026-03-13 | `feature/phase-1-bootstrap` | Fase 1 (ajuste) | Padronização local de credenciais de banco para `postgres/postgres` em `docker-compose.yml` e `application.yml` |
| 2026-03-13 | `feature/phase-1-bootstrap` | Fase 1 (ajuste) | Alteração de porta da app Docker para `8081` para permitir execução simultânea com app local em `8080` |
| 2026-03-14 | `feature/phase-1-bootstrap` | Fase 1 (hardening) | Migration V1 atualizada com `pgcrypto` para suportar `gen_random_uuid()` em ambiente novo |
| 2026-03-14 | `feature/phase-1-bootstrap` | Fase 1 (hardening) | Actuator seguro no base (`show-details: never`) com detalhes apenas em local/test |
| 2026-03-14 | `feature/phase-1-bootstrap` | Fase 1 (robustez) | Docker build fail-fast em dependências Gradle + timeout do wrapper ampliado |
| 2026-03-14 | `feature/phase-1-bootstrap` | Fase 1 (config) | `application-local.yml` versionado e `.gitignore` atualizado |
| 2026-03-14 | `feature/phase-2-user-events` | Fase 2 (T2.1) | Entidade `User`, value objects `UserId`/`UserStatus` — domínio puro sem framework |
| 2026-03-14 | `feature/phase-2-user-events` | Fase 2 (T2.2) | Porta de saída `UserRepository` |
| 2026-03-14 | `feature/phase-2-user-events` | Fase 2 (T2.3) | Use cases, eventos de domínio e exceções |
| 2026-03-14 | `feature/phase-2-user-events` | Fase 2 (T2.4) | Adaptador JPA: `UserJpaEntity`, `UserJpaRepository`, `UserMapper`, `UserPersistenceAdapter` |
| 2026-03-14 | `feature/phase-2-user-events` | Fase 2 (T2.5) | Adaptador web: `UserController`, DTOs, `GlobalExceptionHandler` |
| 2026-03-14 | `feature/phase-2-user-events` | Fase 2 (T2.7) | Testes unitários: 22 casos cobrindo domínio e use cases |
| 2026-03-14 | `feature/phase-2-user-events` | Fase 2 (T2.8) | Testes de integração: 15 casos cobrindo todos os endpoints |
| 2026-03-14 | `feature/phase-2-user-events` | Fase 2 (T2.9) | Coleção Postman, README e DEVELOPMENT_LOG atualizados |
| 2026-03-27 | `feature/phase-3-email-domain` | Fase 3 (T3.1–T3.8) | Domínio de e-mail, portas, use cases, adaptadores JPA, listener assíncrono e scheduler de retry |
| 2026-03-27 | `feature/phase-3-email-domain` | Fase 3 (T3.9) | Testes unitários de `SendEmailUseCase` e `ProcessRetryUseCase` |
| 2026-03-27 | `feature/phase-3-email-domain` | Fase 3 (T3.10) | Teste E2E `EmailFlowIntegrationTest` validando fluxo user -> evento -> persistência de e-mail |

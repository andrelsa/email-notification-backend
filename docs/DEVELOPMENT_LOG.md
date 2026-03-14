# Email Notification Backend — Diário de Desenvolvimento

> Documento atualizado a cada merge na `main`. Serve como referência para compreender o estado atual do projeto e prosseguir com o desenvolvimento.

---

## Índice

- [Visão Geral](#visão-geral)
- [Stack Tecnológica](#stack-tecnológica)
- [Fase 1 — Fundação (Bootstrap & Infraestrutura)](#fase-1--fundação-bootstrap--infraestrutura)
- [Como Rodar o Projeto](#como-rodar-o-projeto)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Banco de Dados](#banco-de-dados)
- [Configurações por Ambiente](#configurações-por-ambiente)
- [Endpoints Disponíveis](#endpoints-disponíveis)
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
- Datasource apontando para Testcontainers (`jdbc:tc:postgresql:16-alpine:///`)
- Flyway habilitado para validar migrations em testes
- `management.endpoint.health.show-details: always` para facilitar diagnóstico em testes

### T1.6 — Migration Flyway V1

Arquivo: `V1__create_initial_schema.sql`

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

# Health check
curl http://localhost:8080/actuator/health

# App em Docker (porta publicada 8081)
curl http://localhost:8081/actuator/health
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

| Método | Path | Descrição | Status |
|--------|------|-----------|--------|
| GET | `http://localhost:8081/actuator/health` | Health check (base sem detalhes; com profile `local` exibe detalhes) | ✅ Fase 1 |
| GET | `http://localhost:8081/actuator/info` | Informações da aplicação (app Docker) | ✅ Fase 1 |
| GET | `http://localhost:8080/actuator/health` | Health check da app local com detalhes | ✅ Fase 1 |

> Endpoints de CRUD de usuários serão adicionados na **Fase 2**.

---

## Próximas Fases

### Fase 2 — Domínio de Usuário + Eventos (Próxima)
- Entidade de domínio `User` e value objects
- Use cases: `CreateUser`, `DeactivateUser`, `DeleteUser`, `GetUser`, `ListUsers`
- Adaptadores JPA (persistence) e REST (controller)
- Publicação de eventos de domínio via `ApplicationEventPublisher`
- Testes unitários e de integração

### Fase 3 — Domínio de E-mail + Retry
- Entidades: `EmailRequest`, `EmailStatus`, `RetryControl`
- Portas: `EmailSender`, `TemplateRenderer`
- Listener de eventos → `SendEmailUseCase`
- Job de retry com `@Scheduled`
- Stubs para envio de e-mail e templates

---

## Histórico de Merges

| Data | Branch | Fase | Descrição |
|------|--------|------|-----------|
| 2026-03-07 | `feature/phase-1-bootstrap` | Fase 1 | Bootstrap do projeto: Spring Boot, Gradle, Docker Compose, Flyway, Actuator, estrutura Clean Architecture |
| 2026-03-13 | `feature/phase-1-bootstrap` | Fase 1 (ajuste) | Padronização local de credenciais de banco para `postgres/postgres` em `docker-compose.yml` e `application.yml` |
| 2026-03-13 | `feature/phase-1-bootstrap` | Fase 1 (ajuste) | Alteração de porta da app Docker para `8081` para permitir execução simultânea com app local em `8080` |


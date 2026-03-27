# Contexto Arquitetural - Email Notification Backend

## Repositório
- owner: andrelsa
- repo: email-notification-backend

## Objetivo
Backend em Kotlin + Spring Boot + Gradle + PostgreSQL para simular cenários reais de envio de e-mail baseado em ações de usuário.

## Casos de uso de e-mail
- UserCreated -> confirmação de cadastro
- UserDeactivated -> notificação de desativação
- UserDeleted -> notificação de exclusão

## Repositórios do produto
1. Back-end (lógica de envio)
2. Front-end/template-service (futuro)

## Premissas
- Baixo acoplamento
- Clean Architecture/SOLID
- Produção-ready
- Templates externos em fase futura (agora stub interno sem HTML fixo)

## Orquestração de containers
- Será utilizado **Docker** para orquestração dos containers no ambiente de desenvolvimento/local.
- Inicialmente, a orquestração será feita com **docker-compose**.
- Serviços previstos na fase inicial:
    - `app` (Spring Boot)
    - `postgres` (banco principal)
- Serviços futuros (quando necessário):
    - `rabbitmq` ou `kafka` para mensageria
    - ferramentas de observabilidade (ex.: prometheus/grafana)

## Banco
Tabelas:
- users
- email_request
- email_status
- retry_control

Status:
- PENDING
- RETRYING
- SENT
- FAILED
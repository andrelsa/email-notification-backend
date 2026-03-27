package com.emailnotification.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

/**
 * Enables Spring's asynchronous method execution capability.
 *
 * Required for the [com.emailnotification.adapter.`in`.event.UserEventListener] which uses
 * [@Async][org.springframework.scheduling.annotation.Async] to process domain events
 * in a background thread after the originating transaction commits, preventing the
 * HTTP request thread from being blocked by email operations.
 *
 * Spring Boot's default async executor ([org.springframework.core.task.SimpleAsyncTaskExecutor])
 * is sufficient for the current stub implementation. Replace with a configured
 * [java.util.concurrent.ThreadPoolExecutor] when moving to production email delivery.
 */
@Configuration
@EnableAsync
class AsyncConfig


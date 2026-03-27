package com.emailnotification.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

/**
 * Enables Spring's asynchronous method execution capability.
 *
 * Required for the [com.emailnotification.adapter.`in`.event.UserEventListener] which uses
 * [@Async][org.springframework.scheduling.annotation.Async] to process domain events
 * in a background thread after the originating transaction commits, preventing the
 * HTTP request thread from being blocked by email operations.
 *
 * Uses a bounded [ThreadPoolTaskExecutor] to avoid unbounded thread creation under load.
 * Pool/queue limits are configurable via `app.async.event-executor.*` properties.
 */
@Configuration
@EnableAsync
class AsyncConfig(
	@Value("\${app.async.event-executor.core-pool-size:2}")
	private val corePoolSize: Int,
	@Value("\${app.async.event-executor.max-pool-size:8}")
	private val maxPoolSize: Int,
	@Value("\${app.async.event-executor.queue-capacity:200}")
	private val queueCapacity: Int,
	@Value("\${app.async.event-executor.thread-name-prefix:event-async-}")
	private val threadNamePrefix: String
) : AsyncConfigurer {

	companion object {
		const val EVENT_ASYNC_EXECUTOR: String = "eventAsyncExecutor"
	}

	@Bean(name = [EVENT_ASYNC_EXECUTOR])
	fun eventAsyncExecutor(): Executor {
		val executor = ThreadPoolTaskExecutor()
		executor.corePoolSize = corePoolSize
		executor.maxPoolSize = maxPoolSize
		executor.setQueueCapacity(queueCapacity)
		executor.setThreadNamePrefix(threadNamePrefix)
		// Backpressure strategy: caller thread executes task when pool/queue is saturated.
		executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
		executor.setWaitForTasksToCompleteOnShutdown(true)
		executor.setAwaitTerminationSeconds(30)
		executor.initialize()
		return executor
	}

	override fun getAsyncExecutor(): Executor = eventAsyncExecutor()
}


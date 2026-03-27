package com.emailnotification.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Enables Spring's scheduled task execution capability.
 *
 * Required for [com.emailnotification.adapter.`in`.scheduler.RetryScheduler] which uses
 * [@Scheduled][org.springframework.scheduling.annotation.Scheduled] to periodically
 * reprocess email requests that failed delivery and are waiting for a retry attempt.
 *
 * Scheduling can be disabled at runtime by setting:
 * ```yaml
 * app:
 *   email:
 *     retry:
 *       enabled: false
 * ```
 * This prevents the [RetryScheduler][com.emailnotification.adapter.`in`.scheduler.RetryScheduler]
 * bean from being created, effectively pausing all retry processing without a redeploy.
 */
@Configuration
@EnableScheduling
class SchedulingConfig


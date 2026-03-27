package com.emailnotification.adapter.`in`.scheduler

import com.emailnotification.application.usecase.ProcessRetryUseCase
import com.emailnotification.domain.port.RetryControlRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Scheduled job that drives the email retry flow.
 *
 * At each fixed-delay interval the scheduler queries for all
 * [com.emailnotification.domain.model.RetryControl] records whose
 * [com.emailnotification.domain.model.RetryControl.nextAttemptAt] has elapsed and whose
 * linked [com.emailnotification.domain.model.EmailRequest] is still
 * [com.emailnotification.domain.model.EmailStatus.RETRYING].
 * For each candidate it delegates a single attempt to [ProcessRetryUseCase].
 *
 * ## Configuration
 * | Property | Default | Description |
 * |---|---|---|
 * | `app.email.retry.enabled` | `true` | Set to `false` to disable the scheduler bean entirely |
 * | `app.email.retry.scheduler.fixed-delay-ms` | `60000` | Milliseconds between end of one run and start of the next |
 *
 * ## Error handling
 * Each retry attempt is wrapped in a try/catch. An unexpected exception from
 * [ProcessRetryUseCase] is logged as ERROR and does not abort processing of the remaining
 * candidates in the same run.
 *
 * ## Rollback
 * Set `app.email.retry.enabled=false` and redeploy to stop all retry processing immediately
 * (see architectural rollback plan).
 */
@Component
@ConditionalOnProperty(
    name = ["app.email.retry.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class RetryScheduler(
    private val retryControlRepository: RetryControlRepository,
    private val processRetryUseCase: ProcessRetryUseCase
) {

    private val log = LoggerFactory.getLogger(RetryScheduler::class.java)

    /**
     * Polls for retry-eligible [com.emailnotification.domain.model.RetryControl] records
     * and processes each one via [ProcessRetryUseCase].
     *
     * Uses `fixedDelay` (not `fixedRate`) to prevent overlapping executions: the next
     * run starts [app.email.retry.scheduler.fixed-delay-ms] **after** the current one ends.
     */
    @Scheduled(fixedDelayString = "\${app.email.retry.scheduler.fixed-delay-ms:60000}")
    fun processRetries() {
        val now = LocalDateTime.now()
        val candidates = retryControlRepository.findAllReadyForRetry(now)

        if (candidates.isEmpty()) {
            log.debug("Retry scheduler: no candidates at {}", now)
            return
        }

        log.info("Retry scheduler: processing {} candidate(s) at {}", candidates.size, now)

        var succeeded = 0
        var failed = 0

        candidates.forEach { retryControl ->
            runCatching {
                processRetryUseCase.execute(retryControl)
                succeeded++
            }.onFailure { ex ->
                failed++
                log.error(
                    "Retry scheduler: unexpected error for retryControl={} emailRequestId={}: {}",
                    retryControl.id, retryControl.emailRequestId, ex.message, ex
                )
            }
        }

        log.info(
            "Retry scheduler: run complete — processed={} succeeded={} failed={}",
            candidates.size, succeeded, failed
        )
    }
}


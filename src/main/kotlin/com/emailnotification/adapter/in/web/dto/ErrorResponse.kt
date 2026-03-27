package com.emailnotification.adapter.`in`.web.dto

import java.time.LocalDateTime
/**
 * Standard error payload returned by [com.emailnotification.adapter.`in`.web.GlobalExceptionHandler].
 */
data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String?,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

package com.emailnotification.adapter.`in`.web

import com.emailnotification.adapter.`in`.web.dto.ErrorResponse
import com.emailnotification.domain.exception.EmailAlreadyExistsException
import com.emailnotification.domain.exception.UserNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Centralised HTTP error mapping for the web adapter.
 *
 * Maps domain exceptions to appropriate HTTP status codes.
 * No domain or application logic lives here — only translation.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /** 404 — resource not found. */
    @ExceptionHandler(UserNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleUserNotFound(ex: UserNotFoundException): ErrorResponse =
        ErrorResponse(
            status = HttpStatus.NOT_FOUND.value(),
            error = "Not Found",
            message = ex.message
        )

    /** 409 — business uniqueness conflict. */
    @ExceptionHandler(EmailAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleEmailAlreadyExists(ex: EmailAlreadyExistsException): ErrorResponse =
        ErrorResponse(
            status = HttpStatus.CONFLICT.value(),
            error = "Conflict",
            message = ex.message
        )

    /** 422 — domain business rule violated (e.g. deactivate a non-ACTIVE user, invalid UUID). */
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    fun handleIllegalArgument(ex: IllegalArgumentException): ErrorResponse =
        ErrorResponse(
            status = HttpStatus.UNPROCESSABLE_ENTITY.value(),
            error = "Unprocessable Entity",
            message = ex.message
        )

    /** 400 — request body failed Bean Validation (@NotBlank, @Email, etc.). */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(ex: MethodArgumentNotValidException): ErrorResponse {
        val details = ex.bindingResult.allErrors.joinToString("; ") { error ->
            when (error) {
                is FieldError -> "${error.field}: ${error.defaultMessage}"
                else -> error.defaultMessage ?: "Validation error"
            }
        }
        return ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
            message = details
        )
    }
}

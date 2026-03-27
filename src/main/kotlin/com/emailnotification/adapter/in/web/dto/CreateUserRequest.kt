package com.emailnotification.adapter.`in`.web.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request body for POST /users.
 *
 * Validated at the controller boundary — domain layer receives clean primitives.
 */
data class CreateUserRequest(

    @field:NotBlank(message = "Name is required")
    @field:Size(max = 255, message = "Name must not exceed 255 characters")
    val name: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid e-mail format")
    @field:Size(max = 255, message = "Email must not exceed 255 characters")
    val email: String
)


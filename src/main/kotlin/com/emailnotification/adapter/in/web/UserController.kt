package com.emailnotification.adapter.`in`.web

import com.emailnotification.adapter.`in`.web.dto.CreateUserRequest
import com.emailnotification.adapter.`in`.web.dto.UserResponse
import com.emailnotification.application.usecase.CreateUserUseCase
import com.emailnotification.application.usecase.DeactivateUserUseCase
import com.emailnotification.application.usecase.DeleteUserUseCase
import com.emailnotification.application.usecase.GetUserUseCase
import com.emailnotification.application.usecase.ListUsersUseCase
import com.emailnotification.domain.model.UserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Input adapter — exposes the User use cases as a REST API.
 *
 * Responsibilities:
 *  - Parse and validate HTTP input (DTOs + Bean Validation)
 *  - Delegate to use cases (no business logic here)
 *  - Map domain results to HTTP responses
 *
 * Error handling is centralised in [GlobalExceptionHandler].
 */
@RestController
@RequestMapping("/users")
class UserController(
    private val createUserUseCase: CreateUserUseCase,
    private val getUserUseCase: GetUserUseCase,
    private val listUsersUseCase: ListUsersUseCase,
    private val deactivateUserUseCase: DeactivateUserUseCase,
    private val deleteUserUseCase: DeleteUserUseCase
) {

    /**
     * POST /users
     * Creates a new user. Returns 201 with the created resource.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@RequestBody @Valid request: CreateUserRequest): UserResponse {
        val user = createUserUseCase.execute(request.name, request.email)
        return UserResponse.from(user)
    }

    /**
     * GET /users
     * Lists all non-deleted users.
     */
    @GetMapping
    fun listUsers(): List<UserResponse> =
        listUsersUseCase.execute().map(UserResponse::from)

    /**
     * GET /users/{publicId}
     * Returns a single user by public UUID. 404 if not found.
     */
    @GetMapping("/{publicId}")
    fun getUser(@PathVariable publicId: String): UserResponse {
        val user = getUserUseCase.execute(UserId.from(publicId))
        return UserResponse.from(user)
    }

    /**
     * PATCH /users/{publicId}/deactivate
     * Transitions the user from ACTIVE to INACTIVE.
     * 404 if not found, 422 if already inactive or deleted.
     */
    @PatchMapping("/{publicId}/deactivate")
    fun deactivateUser(@PathVariable publicId: String): UserResponse {
        val user = deactivateUserUseCase.execute(UserId.from(publicId))
        return UserResponse.from(user)
    }

    /**
     * DELETE /users/{publicId}
     * Soft-deletes the user (ACTIVE/INACTIVE -> DELETED). Returns the final state.
     * 404 if not found, 422 if already deleted.
     */
    @DeleteMapping("/{publicId}")
    fun deleteUser(@PathVariable publicId: String): UserResponse {
        val user = deleteUserUseCase.execute(UserId.from(publicId))
        return UserResponse.from(user)
    }
}


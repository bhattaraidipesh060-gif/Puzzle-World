package com.jeiu.puzzleworld

import kotlinx.serialization.Serializable

/** Request body for register / login (username + password). */
@Serializable
data class AuthRequest(
    val username: String,
    val password: String
)

/** User profile returned by the backend. */
@Serializable
data class UserProfile(
    val user_id: String,
    val username: String? = null,
    val email: String? = null,
    val has_username: Boolean = false,
    val language: String? = null
)

/** Register/login response: a signed JWT plus the user profile. */
@Serializable
data class AuthResponse(val token: String, val user: UserProfile)

/** GET /api/auth/me response (session validation). */
@Serializable
data class MeResponse(val user: UserProfile)

/** POST /api/auth/set-username request + response. */
@Serializable
data class SetUsernameRequest(val username: String)

@Serializable
data class SetUsernameResponse(val ok: Boolean = false, val user: UserProfile)

/** POST /api/auth/language request + response. */
@Serializable
data class SetLanguageRequest(val language: String)

@Serializable
data class SetLanguageResponse(val ok: Boolean = false, val language: String? = null)

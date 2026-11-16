package com.jeiu.puzzleworld

import kotlinx.serialization.Serializable

/** Request body for register (email) / login (email or username). */
@Serializable
data class AuthRequest(
    val email: String? = null,
    val username: String? = null,
    val password: String
)

/** User profile returned by the backend (username is null until the user picks one). */
@Serializable
data class UserProfile(
    val user_id: String,
    val email: String,
    val username: String? = null,
    val has_username: Boolean = false
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

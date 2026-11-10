package com.jeiu.puzzleworld

import kotlinx.serialization.Serializable

/** Request body for both /api/auth/register and /api/auth/login. */
@Serializable
data class AuthRequest(val username: String, val password: String)

/** User profile returned by the backend. */
@Serializable
data class UserProfile(val user_id: String, val username: String)

/** Register/login response: a signed JWT plus the user profile. */
@Serializable
data class AuthResponse(val token: String, val user: UserProfile)

/** GET /api/auth/me response (session validation). */
@Serializable
data class MeResponse(val user: UserProfile)

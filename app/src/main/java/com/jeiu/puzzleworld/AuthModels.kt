package com.jeiu.puzzleworld

import kotlinx.serialization.Serializable

/** Request body for register / login (email + password). */
@Serializable
data class AuthRequest(
    val email: String,
    val password: String
)

/** Register response: a message only (no token) — the user must verify the OTP. */
@Serializable
data class RegisterResponse(val message: String? = null, val email: String? = null)

/** User profile returned by the backend (username is null until the user picks one). */
@Serializable
data class UserProfile(
    val user_id: String,
    val email: String,
    val username: String? = null,
    val has_username: Boolean = false,
    val language: String? = null
)

/** Register/login/verify-otp response: a signed JWT plus the user profile. */
@Serializable
data class AuthResponse(val token: String, val user: UserProfile)

/** GET /api/auth/me response (session validation). */
@Serializable
data class MeResponse(val user: UserProfile)

/** POST /api/auth/verify-otp request. */
@Serializable
data class VerifyOtpRequest(val email: String, val otp: String)

/** POST /api/auth/resend-otp request. */
@Serializable
data class ResendOtpRequest(val email: String)

/** POST /api/auth/set-username request + response. */
@Serializable
data class SetUsernameRequest(val username: String)

@Serializable
data class SetUsernameResponse(val ok: Boolean = false, val user: UserProfile)

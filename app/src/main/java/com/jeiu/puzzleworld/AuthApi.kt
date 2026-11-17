package com.jeiu.puzzleworld

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit interface for the backend authentication endpoints.
 * All requests flow through the shared OkHttp client, whose AuthInterceptor
 * attaches the stored bearer token (used by /me and future authenticated calls).
 */
interface AuthApi {

    @POST("api/auth/register")
    suspend fun register(@Body body: AuthRequest): RegisterResponse

    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyOtpRequest): AuthResponse

    @POST("api/auth/resend-otp")
    suspend fun resendOtp(@Body body: ResendOtpRequest): RegisterResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: AuthRequest): AuthResponse

    @GET("api/auth/me")
    suspend fun me(): MeResponse

    @POST("api/auth/set-username")
    suspend fun setUsername(@Body body: SetUsernameRequest): SetUsernameResponse
}

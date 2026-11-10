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
    suspend fun register(@Body body: AuthRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: AuthRequest): AuthResponse

    @GET("api/auth/me")
    suspend fun me(): MeResponse
}

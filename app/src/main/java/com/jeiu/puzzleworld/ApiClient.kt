package com.jeiu.puzzleworld

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit/OkHttp stack. The AuthInterceptor attaches the stored
 * bearer token to every request; the kotlinx-serialization converter reuses the
 * project's existing serialization setup (no reflection, R8-safe).
 */
object ApiClient {

    // Backend base URL — set at build time via BuildConfig.API_BASE_URL.
    // Debug defaults to emulator loopback (10.0.2.2); for a release APK pointing at
    // the hosted backend, build with: ./gradlew assembleRelease -PPW_API_URL=https://your-backend.onrender.com/
    const val BASE_URL = BuildConfig.API_BASE_URL

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor { SessionManager.token() })
            .build()
    }

    val authApi: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApi::class.java)
    }
}

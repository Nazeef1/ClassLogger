package com.example.classlogger.network

import com.example.classlogger.models.FaceVerificationResponse
import com.example.classlogger.models.FaceEncodingRequest
import com.example.classlogger.models.FaceEncodingResponse
import com.example.classlogger.models.FaceVerificationRequest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// Retrofit API Interface
interface FaceNetApi {

    @POST("/verify")
    suspend fun verifyFace(
        @Body request: FaceVerificationRequest
    ): Response<FaceVerificationResponse>

    @POST("/encode")
    suspend fun encodeFace(
        @Body request: FaceEncodingRequest
    ): Response<FaceEncodingResponse>
}

// Retrofit Client Builder
object FaceNetApiClient {

    private const val TIMEOUT_SECONDS = 30L

    // Update this with your ngrok URL
    // Example: "https://your-ngrok-id.ngrok.io/"
    private var BASE_URL = "https://spatially-bridgelike-lona.ngrok-free.dev"

    fun setBaseUrl(url: String) {
        BASE_URL = if (url.endsWith("/")) url else "$url/"
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: FaceNetApi by lazy {
        retrofit.create(FaceNetApi::class.java)
    }
}
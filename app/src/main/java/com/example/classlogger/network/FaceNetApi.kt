package com.example.classlogger.network

import com.example.classlogger.models.*
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
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

    @Multipart
    @POST("/predict")
    suspend fun predictFace(
        @Part file: MultipartBody.Part
    ): Response<PredictResponse>

    // NEW: Check if name exists in NPZ file
    @POST("/check-name")
    suspend fun checkName(
        @Body request: CheckNameRequest
    ): Response<CheckNameResponse>

    // NEW: Update label from name to Firebase ID
    @POST("/update-label")
    suspend fun updateLabel(
        @Body request: UpdateLabelRequest
    ): Response<UpdateLabelResponse>
}

// Retrofit Client Builder
object FaceNetApiClient {

    // Increased timeout for full resolution image processing
    private const val TIMEOUT_SECONDS = 60L

    // Update this with your Cloudflare tunnel URL
    // Example: "https://your-tunnel-name.trycloudflare.com/"
    private var BASE_URL = "https://sensitive-convergence-bags-human.trycloudflare.com"

    fun setBaseUrl(url: String) {
        BASE_URL = if (url.endsWith("/")) url else "$url/"
    }

    fun getBaseUrl(): String = BASE_URL

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
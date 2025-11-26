package com.example.classlogger.repository

import android.util.Log
import com.example.classlogger.models.FaceVerificationResponse
import com.example.classlogger.models.FaceEncodingRequest
import com.example.classlogger.models.FaceEncodingResponse
import com.example.classlogger.models.FaceVerificationRequest
import com.example.classlogger.models.PredictResponse
import com.example.classlogger.network.FaceNetApi
import com.example.classlogger.network.FaceNetApiClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class FaceRecognitionRepository {

    private val api: FaceNetApi = FaceNetApiClient.api
    private val TAG = "FaceRecognitionRepository"

    /**
     * Verify if the captured face matches the student's registered face
     * (Old method - kept for backward compatibility)
     */
    suspend fun verifyFace(studentId: String, capturedImageBase64: String): Result<FaceVerificationResponse> {
        return try {
            val request = FaceVerificationRequest(
                studentId = studentId,
                imageBase64 = capturedImageBase64
            )

            val response = api.verifyFace(request)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Verification failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Encode a face image to get face embedding for registration
     */
    suspend fun encodeFace(imageBase64: String): Result<FaceEncodingResponse> {
        return try {
            val request = FaceEncodingRequest(imageBase64 = imageBase64)

            val response = api.encodeFace(request)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Encoding failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * NEW METHOD: Predict face identity using raw image file
     * Sends raw image bytes to /predict endpoint
     * Returns label (student_id or "unknown") and confidence score
     */
    suspend fun predictFace(imageBytes: ByteArray): Result<PredictResponse> {
        return try {
            Log.d(TAG, "=== PREDICT REQUEST ===")
            Log.d(TAG, "Image bytes size: ${imageBytes.size}")
            Log.d(TAG, "Image bytes first 20: ${imageBytes.take(20)}")
            Log.d(TAG, "Sending to /predict endpoint...")

            // Create multipart body with raw image
            val requestBody = imageBytes.toRequestBody("image/jpeg".toMediaType())
            val part = MultipartBody.Part.createFormData("file", "selfie.jpg", requestBody)

            val response = api.predictFace(part)

            Log.d(TAG, "Response code: ${response.code()}")
            Log.d(TAG, "Response successful: ${response.isSuccessful}")

            if (response.isSuccessful && response.body() != null) {
                val predictResponse = response.body()!!
                Log.d(TAG, "=== PREDICT RESPONSE ===")
                Log.d(TAG, "Label: ${predictResponse.label}")
                Log.d(TAG, "Confidence: ${predictResponse.confidence}")
                Result.success(predictResponse)
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.message()
                Log.e(TAG, "=== PREDICT ERROR ===")
                Log.e(TAG, "Error: $errorMsg")
                Result.failure(Exception("Prediction failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during predict: ${e.message}", e)
            Result.failure(e)
        }
    }
}
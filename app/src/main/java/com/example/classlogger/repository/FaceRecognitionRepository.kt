package com.example.classlogger.repository

import com.example.classlogger.models.FaceVerificationResponse
import com.example.classlogger.models.FaceEncodingRequest
import com.example.classlogger.models.FaceEncodingResponse
import com.example.classlogger.models.FaceVerificationRequest
import com.example.classlogger.network.FaceNetApiClient

class FaceRecognitionRepository {

    private val api = FaceNetApiClient.api

    /**
     * Verify if the captured face matches the student's registered face
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
}
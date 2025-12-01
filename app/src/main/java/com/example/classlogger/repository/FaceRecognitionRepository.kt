package com.example.classlogger.repository

import android.util.Log
import com.example.classlogger.models.*
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
     * Predict face identity using raw image file
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

    /**
     * NEW: Check if a name exists in the NPZ file
     * This should be called BEFORE creating Firebase account
     */
    suspend fun checkNameExists(name: String): Result<CheckNameResponse> {
        return try {
            Log.d(TAG, "=== CHECK NAME REQUEST ===")
            Log.d(TAG, "Checking if name exists: $name")

            val request = CheckNameRequest(name = name)
            val response = api.checkName(request)

            Log.d(TAG, "Response code: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val checkResponse = response.body()!!
                Log.d(TAG, "=== CHECK NAME RESPONSE ===")
                Log.d(TAG, "Exists: ${checkResponse.exists}")
                Log.d(TAG, "Message: ${checkResponse.message}")
                Result.success(checkResponse)
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.message()
                Log.e(TAG, "Check name failed: $errorMsg")
                Result.failure(Exception("Check name failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during check name: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * NEW: Update label in NPZ file from name to Firebase ID
     * This should be called AFTER Firebase account is created
     */
    suspend fun updateLabel(oldLabel: String, newLabel: String): Result<UpdateLabelResponse> {
        return try {
            Log.d(TAG, "=== UPDATE LABEL REQUEST ===")
            Log.d(TAG, "Old Label (name): $oldLabel")
            Log.d(TAG, "New Label (Firebase ID): $newLabel")

            val request = UpdateLabelRequest(
                old_label = oldLabel,
                new_label = newLabel
            )

            val response = api.updateLabel(request)

            Log.d(TAG, "Response code: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val updateResponse = response.body()!!
                Log.d(TAG, "=== UPDATE LABEL RESPONSE ===")
                Log.d(TAG, "Success: ${updateResponse.success}")
                Log.d(TAG, "Message: ${updateResponse.message}")
                Log.d(TAG, "Updated Count: ${updateResponse.updatedCount}")
                Result.success(updateResponse)
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.message()
                Log.e(TAG, "Update label failed: $errorMsg")
                Result.failure(Exception("Update label failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during update label: ${e.message}", e)
            Result.failure(e)
        }
    }
}
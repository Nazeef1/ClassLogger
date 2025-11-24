package com.example.classlogger.models

data class FaceEncodingResponse(
    val encoding: String, // Base64 encoded embedding
    val success: Boolean,
    val message: String
)

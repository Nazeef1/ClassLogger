package com.example.classlogger.models

data class FaceVerificationResponse(
    val isMatch: Boolean,
    val confidence: Float,
    val message: String
)
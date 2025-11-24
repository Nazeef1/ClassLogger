package com.example.classlogger.models

data class FaceVerificationRequest(
    val studentId: String,
    val imageBase64: String
)

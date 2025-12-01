package com.example.classlogger.models

data class UpdateLabelResponse(
    val success: Boolean,
    val message: String,
    val updatedCount: Int
)

package com.example.classlogger.models

data class PredictResponse(
    val label: String,        // student_id or "unknown"
    val confidence: Float     // 0.0 to 1.0
)
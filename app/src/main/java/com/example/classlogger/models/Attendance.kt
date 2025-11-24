package com.example.classlogger.models

data class Attendance(
    val id: String = "",  // Firestore document ID
    val sessionId: String = "",
    val studentId: String = "",
    val status: AttendanceStatus = AttendanceStatus.ABSENT,
    val markedAt: Long = 0,
    val selfieBase64: String = "",  // Changed from selfieUrl - stores image as Base64 string
    val verificationScore: Float = 0f,
    val markedBy: MarkedBy = MarkedBy.STUDENT,
    val overriddenBy: String = ""
)
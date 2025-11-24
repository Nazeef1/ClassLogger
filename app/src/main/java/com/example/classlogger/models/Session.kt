package com.example.classlogger.models

data class Session(
    val id: String = "",
    val teacherId: String = "",
    val subjectId: String = "",
    val classroomId: String = "",
    val date: String = "", // YYYY-MM-DD format
    val startTime: Long = 0,
    val endTime: Long = 0,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val wifiSSID: String = "",
    val wifiBSSID: String = "",
    val attendanceWindow: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

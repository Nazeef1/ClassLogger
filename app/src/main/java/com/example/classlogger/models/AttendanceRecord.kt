package com.example.classlogger.models

data class AttendanceRecord(
    val sessionId: String = "",
    val subjectName: String = "",
    val date: String = "",
    val startTime: Long = 0,
    val endTime: Long = 0,
    val status: AttendanceStatus = AttendanceStatus.ABSENT,
    val markedAt: Long = 0,
    val selfieUrl: String = ""
)

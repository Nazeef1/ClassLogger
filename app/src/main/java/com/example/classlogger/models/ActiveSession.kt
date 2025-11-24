package com.example.classlogger.models

data class ActiveSession(
    val sessionId: String = "",
    val subjectName: String = "",
    val teacherName: String = "",
    val classroomName: String = "",
    val startTime: Long = 0,
    val endTime: Long = 0
)

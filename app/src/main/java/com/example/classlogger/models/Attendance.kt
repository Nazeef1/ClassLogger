package com.example.classlogger.models


data class Attendance(
    val id: String = "",
    val sessionId: String = "",
    val studentId: String = "",
    val classroomId: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val status: AttendanceStatus = AttendanceStatus.ABSENT,
    val markedAt: Long = 0,
    val markedBy: MarkedBy = MarkedBy.STUDENT,
    val selfieUrl: String = "",
    val overriddenBy: String = ""
)
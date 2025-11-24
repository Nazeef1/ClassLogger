package com.example.classlogger.models

data class SubjectAttendance(
    val subjectId: String = "",
    val subjectName: String = "",
    val subjectCode: String = "",
    val totalClasses: Int = 0,
    val attendedClasses: Int = 0,
    val attendancePercentage: Float = 0f
)

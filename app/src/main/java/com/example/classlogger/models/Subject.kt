package com.example.classlogger.models

data class Subject(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val classroomId: String = "",
    val teacherId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

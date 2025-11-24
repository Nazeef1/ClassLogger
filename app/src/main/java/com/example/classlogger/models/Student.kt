package com.example.classlogger.models

data class Student(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val rollNumber: String = "",
    val classrooms: List<String> = emptyList(),
    val faceEncoding: String = "", // Base64 encoded face embedding
    val createdAt: Long = System.currentTimeMillis()
)

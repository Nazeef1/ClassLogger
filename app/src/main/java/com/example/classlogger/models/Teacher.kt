package com.example.classlogger.models

data class Teacher(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val classrooms: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

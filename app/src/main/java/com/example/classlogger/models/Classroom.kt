package com.example.classlogger.models

data class Classroom(
    val id: String = "",
    val name: String = "",
    val wifiSSID: String = "",
    val wifiBSSID: String = "",
    val teachers: List<String> = emptyList(),
    val students: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

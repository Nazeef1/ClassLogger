package com.example.classlogger.ui.teacher

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.classlogger.databinding.ActivityTeacherViewAttendanceBinding
import com.example.classlogger.models.Classroom
import com.example.classlogger.models.Student
import com.example.classlogger.repository.FirebaseRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TeacherViewAttendanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherViewAttendanceBinding
    private lateinit var repository: FirebaseRepository

    private var teacherId: String = ""
    private var selectedClassroom: Classroom? = null
    private var selectedStudent: Student? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherViewAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()
        teacherId = intent.getStringExtra("teacherId") ?: ""

        setupUI()
        loadClassrooms()
    }

    private fun setupUI() {
        binding.btnSelectClassroom.setOnClickListener {
            showClassroomSelector()
        }

        binding.btnSelectStudent.setOnClickListener {
            if (selectedClassroom != null) {
                showStudentSelector()
            } else {
                Toast.makeText(this, "Please select classroom first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadClassrooms() {
        lifecycleScope.launch {
            repository.getTeacherClassrooms(teacherId)
                .onSuccess { classrooms ->
                    // Classrooms loaded
                }
                .onFailure { error ->
                    Toast.makeText(
                        this@TeacherViewAttendanceActivity,
                        "Failed to load classrooms",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun showClassroomSelector() {
        lifecycleScope.launch {
            repository.getTeacherClassrooms(teacherId).onSuccess { classrooms ->
                val classroomNames = classrooms.map { it.name }.toTypedArray()

                MaterialAlertDialogBuilder(this@TeacherViewAttendanceActivity)
                    .setTitle("Select Classroom")
                    .setItems(classroomNames) { _, which ->
                        selectedClassroom = classrooms[which]
                        binding.tvSelectedClassroom.text = classrooms[which].name
                        selectedStudent = null
                        binding.tvSelectedStudent.text = "No student selected"
                    }
                    .show()
            }
        }
    }

    private fun showStudentSelector() {
        selectedClassroom?.let { classroom ->
            lifecycleScope.launch {
                try {
                    val students = mutableListOf<Student>()
                    for (studentId in classroom.students) {
                        val snapshot = repository.firestore
                            .collection("students")
                            .document(studentId)
                            .get()
                            .await()

                        if (snapshot.exists()) {
                            val student = Student(
                                id = studentId,
                                name = snapshot.getString("name") ?: "",
                                email = snapshot.getString("email") ?: "",
                                phone = snapshot.getString("phone") ?: "",
                                rollNumber = snapshot.getString("rollNumber") ?: "",
                                classrooms = (snapshot.get("classrooms") as? List<String>) ?: emptyList(),
                                faceEncoding = snapshot.getString("faceEncoding") ?: ""
                            )
                            students.add(student)
                        }
                    }

                    val studentNames = students.map { "${it.name} (${it.rollNumber})" }.toTypedArray()

                    MaterialAlertDialogBuilder(this@TeacherViewAttendanceActivity)
                        .setTitle("Select Student")
                        .setItems(studentNames) { _, which ->
                            selectedStudent = students[which]
                            binding.tvSelectedStudent.text = studentNames[which]
                            Toast.makeText(
                                this@TeacherViewAttendanceActivity,
                                "Student selected. Feature coming soon!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@TeacherViewAttendanceActivity,
                        "Error loading students: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}

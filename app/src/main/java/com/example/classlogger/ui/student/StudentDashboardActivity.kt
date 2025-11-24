package com.example.classlogger.ui.student

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.classlogger.databinding.ActivityStudentDashboardBinding
import com.example.classlogger.repository.FirebaseRepository
import com.example.classlogger.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class StudentDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: FirebaseRepository

    private var studentId: String = ""
    private var studentName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        repository = FirebaseRepository()

        studentId = intent.getStringExtra("studentId") ?: auth.currentUser?.uid ?: ""

        if (studentId.isEmpty()) {
            navigateToLogin()
            return
        }

        loadStudentInfo()
        setupUI()
    }

    private fun setupUI() {
        binding.cardMarkAttendance.setOnClickListener {
            val intent = Intent(this, StudentActiveSessionsActivity::class.java)
            intent.putExtra("studentId", studentId)
            startActivity(intent)
        }

        binding.cardViewAttendance.setOnClickListener {
            val intent = Intent(this, StudentAttendanceHistoryActivity::class.java)
            intent.putExtra("studentId", studentId)
            startActivity(intent)
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun loadStudentInfo() {
        lifecycleScope.launch {
            try {
                val studentDoc = repository.firestore
                    .collection("students")
                    .document(studentId)
                    .get()
                    .await()

                if (studentDoc.exists()) {
                    studentName = studentDoc.getString("name") ?: "Student"
                    val rollNumber = studentDoc.getString("rollNumber") ?: ""
                    val email = studentDoc.getString("email") ?: ""

                    binding.tvWelcome.text = "Welcome, $studentName"
                    binding.tvRollNumber.text = "Roll No: $rollNumber"
                    binding.tvEmail.text = email
                } else {
                    Toast.makeText(this@StudentDashboardActivity,
                        "Failed to load student info", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@StudentDashboardActivity,
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logout() {
        auth.signOut()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to dashboard
        loadStudentInfo()
    }
}
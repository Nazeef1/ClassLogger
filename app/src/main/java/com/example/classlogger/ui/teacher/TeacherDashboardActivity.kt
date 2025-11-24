package com.example.classlogger.ui.teacher

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.classlogger.databinding.ActivityTeacherDashboardBinding
import com.example.classlogger.repository.FirebaseRepository
import com.example.classlogger.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TeacherDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: FirebaseRepository

    private var teacherId: String = ""
    private var teacherName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        repository = FirebaseRepository()

        teacherId = intent.getStringExtra("teacherId") ?: auth.currentUser?.uid ?: ""

        if (teacherId.isEmpty()) {
            navigateToLogin()
            return
        }

        loadTeacherInfo()
        setupUI()
    }

    private fun setupUI() {
        binding.cardTakeAttendance.setOnClickListener {
            val intent = Intent(this, TeacherCreateSessionActivity::class.java)
            intent.putExtra("teacherId", teacherId)
            startActivity(intent)
        }

        binding.cardViewAttendance.setOnClickListener {
            val intent = Intent(this, TeacherViewAttendanceActivity::class.java)
            intent.putExtra("teacherId", teacherId)
            startActivity(intent)
        }

        binding.cardActiveSessions.setOnClickListener {
            val intent = Intent(this, TeacherActiveSessionsActivity::class.java)
            intent.putExtra("teacherId", teacherId)
            startActivity(intent)
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun loadTeacherInfo() {
        lifecycleScope.launch {
            try {
                val teacherDoc = repository.firestore
                    .collection("teachers")
                    .document(teacherId)
                    .get()
                    .await()

                if (teacherDoc.exists()) {
                    teacherName = teacherDoc.getString("name") ?: "Teacher"
                    val email = teacherDoc.getString("email") ?: ""

                    binding.tvWelcome.text = "Welcome, $teacherName"
                    binding.tvEmail.text = email
                } else {
                    Toast.makeText(this@TeacherDashboardActivity,
                        "Failed to load teacher info", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TeacherDashboardActivity,
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
}
package com.example.classlogger.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.classlogger.databinding.ActivityLoginBinding
import com.example.classlogger.repository.FirebaseRepository
import com.example.classlogger.ui.teacher.TeacherDashboardActivity
import com.example.classlogger.ui.student.StudentDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: FirebaseRepository

    private var isTeacherLogin = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        repository = FirebaseRepository()

        // Check if already logged in
        if (auth.currentUser != null) {
            // For now, just proceed - you can add user type check later
        }

        setupUI()
    }

    private fun setupUI() {
        // Toggle between Teacher and Student login
        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            isTeacherLogin = checkedId == binding.radioTeacher.id
            updateUI()
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInput(email, password)) {
                login(email, password)
            }
        }

        binding.tvRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            intent.putExtra("isTeacher", isTeacherLogin)
            startActivity(intent)
        }

        updateUI()
    }

    private fun updateUI() {
        binding.tvTitle.text = if (isTeacherLogin) "Teacher Login" else "Student Login"
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            binding.tilEmail.error = "Email is required"
            return false
        } else {
            binding.tilEmail.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Password is required"
            return false
        } else if (password.length < 6) {
            binding.tilPassword.error = "Password must be at least 6 characters"
            return false
        } else {
            binding.tilPassword.error = null
        }

        return true
    }

    private fun login(email: String, password: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        lifecycleScope.launch {
            try {
                if (isTeacherLogin) {
                    repository.loginTeacher(email, password)
                        .onSuccess { teacher ->
                            navigateToTeacherDashboard(teacher.id)
                        }
                        .onFailure { error ->
                            showError(error.message ?: "Login failed")
                        }
                } else {
                    repository.loginStudent(email, password)
                        .onSuccess { student ->
                            navigateToStudentDashboard(student.id)
                        }
                        .onFailure { error ->
                            showError(error.message ?: "Login failed")
                        }
                }
            } catch (e: Exception) {
                showError("An error occurred: ${e.message}")
            }
        }
    }

    private fun navigateToTeacherDashboard(teacherId: String) {
        val intent = Intent(this, TeacherDashboardActivity::class.java)
        intent.putExtra("teacherId", teacherId)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToStudentDashboard(studentId: String) {
        val intent = Intent(this, StudentDashboardActivity::class.java)
        intent.putExtra("studentId", studentId)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.btnLogin.isEnabled = true
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
package com.example.classlogger.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.classlogger.databinding.ActivityRegisterBinding
import com.example.classlogger.models.Student
import com.example.classlogger.models.Teacher
import com.example.classlogger.repository.FaceRecognitionRepository
import com.example.classlogger.repository.FirebaseRepository
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var faceRepository: FaceRecognitionRepository

    private var isTeacher = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()
        faceRepository = FaceRecognitionRepository()

        isTeacher = intent.getBooleanExtra("isTeacher", true)

        setupUI()
    }

    private fun setupUI() {
        binding.tvTitle.text = if (isTeacher) "Teacher Registration" else "Student Registration"

        // Show/hide student-specific fields
        if (isTeacher) {
            binding.tilRollNumber.visibility = View.GONE
            binding.cardInfo.visibility = View.GONE
        } else {
            binding.tilRollNumber.visibility = View.VISIBLE
            binding.cardInfo.visibility = View.VISIBLE
        }

        binding.btnRegister.setOnClickListener {
            register()
        }

        binding.tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun validateInput(): Boolean {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        if (name.isEmpty()) {
            binding.tilName.error = "Name is required"
            return false
        } else {
            binding.tilName.error = null
        }

        if (email.isEmpty()) {
            binding.tilEmail.error = "Email is required"
            return false
        } else {
            binding.tilEmail.error = null
        }

        if (phone.isEmpty()) {
            binding.tilPhone.error = "Phone is required"
            return false
        } else {
            binding.tilPhone.error = null
        }

        if (!isTeacher) {
            val rollNumber = binding.etRollNumber.text.toString().trim()
            if (rollNumber.isEmpty()) {
                binding.tilRollNumber.error = "Roll number is required"
                return false
            } else {
                binding.tilRollNumber.error = null
            }

            // REMOVED: No longer checking for captured bitmap
        }

        if (password.isEmpty() || password.length < 6) {
            binding.tilPassword.error = "Password must be at least 6 characters"
            return false
        } else {
            binding.tilPassword.error = null
        }

        if (password != confirmPassword) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            return false
        } else {
            binding.tilConfirmPassword.error = null
        }

        return true
    }

    private fun register() {
        if (!validateInput()) return

        binding.progressBar.visibility = View.VISIBLE
        binding.btnRegister.isEnabled = false

        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        lifecycleScope.launch {
            try {
                if (isTeacher) {
                    val teacher = Teacher(
                        name = name,
                        email = email,
                        phone = phone,
                        classrooms = emptyList()
                    )

                    val result = repository.registerTeacher(email, password, teacher)

                    if (result.isSuccess) {
                        showSuccess("Teacher registered successfully!")
                    } else {
                        showError("Registration failed: ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    val rollNumber = binding.etRollNumber.text.toString().trim()
                    // NEW FLOW: Check name first, then register
                    registerStudent(name, email, phone, rollNumber, password)
                }
            } catch (e: Exception) {
                showError("Error: ${e.message}")
            }
        }
    }

    private suspend fun registerStudent(
        name: String,
        email: String,
        phone: String,
        rollNumber: String,
        password: String
    ) {
        // STEP 1: Check if name exists in NPZ file
        val checkResult = faceRepository.checkNameExists(name)

        if (checkResult.isFailure) {
            showError("Failed to verify name: ${checkResult.exceptionOrNull()?.message}")
            return
        }

        val checkResponse = checkResult.getOrNull()
        if (checkResponse == null || !checkResponse.exists) {
            showError("Name not found in face database. Please contact admin to add your face data first.")
            return
        }

        // STEP 2: Name exists! Create Firebase account
        val student = Student(
            name = name,
            email = email,
            phone = phone,
            rollNumber = rollNumber,
            classrooms = emptyList(),
            faceEncoding = "" // No longer storing face encoding here
        )

        val registerResult = repository.registerStudent(email, password, student)

        if (registerResult.isFailure) {
            showError("Registration failed: ${registerResult.exceptionOrNull()?.message}")
            return
        }

        // STEP 3: Get the Firebase-generated student ID
        val studentId = registerResult.getOrNull()
        if (studentId == null) {
            showError("Failed to get student ID after registration")
            return
        }

        // STEP 4: Update NPZ label from name → Firebase ID
        val updateResult = faceRepository.updateLabel(
            oldLabel = name,
            newLabel = studentId
        )

        if (updateResult.isFailure) {
            showError("Warning: Registration successful but failed to update face database. Contact admin.")
            // Still show success since Firebase registration worked
            showSuccess("Student registered successfully! (Warning: Face database update pending)")
            return
        }

        val updateResponse = updateResult.getOrNull()
        if (updateResponse == null || !updateResponse.success) {
            showError("Warning: Registration successful but face database update failed.")
            showSuccess("Student registered successfully! (Warning: Face database update pending)")
            return
        }

        // SUCCESS: Everything worked!
        showSuccess("Student registered successfully! Face data linked to your account.")
    }

    private fun showSuccess(message: String) {
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Navigate to login
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.btnRegister.isEnabled = true
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
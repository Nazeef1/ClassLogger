package com.example.classlogger.ui.auth

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Base64
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.classlogger.databinding.ActivityRegisterBinding
import com.example.classlogger.models.Student
import com.example.classlogger.models.Teacher
import com.example.classlogger.repository.FaceRecognitionRepository
import com.example.classlogger.repository.FirebaseRepository
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var faceRepository: FaceRecognitionRepository

    private var isTeacher = true
    private var capturedBitmap: Bitmap? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val CAMERA_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()
        faceRepository = FaceRecognitionRepository()
        cameraExecutor = Executors.newSingleThreadExecutor()

        isTeacher = intent.getBooleanExtra("isTeacher", true)

        setupUI()
    }

    private fun setupUI() {
        binding.tvTitle.text = if (isTeacher) "Teacher Registration" else "Student Registration"

        // Show/hide student-specific fields
        if (isTeacher) {
            binding.tilRollNumber.visibility = View.GONE
            binding.cardCamera.visibility = View.GONE
        } else {
            binding.tilRollNumber.visibility = View.VISIBLE
            binding.cardCamera.visibility = View.VISIBLE

            binding.btnCapturePhoto.setOnClickListener {
                if (checkCameraPermission()) {
                    startCamera()
                } else {
                    requestCameraPermission()
                }
            }

            binding.btnRetakePhoto.setOnClickListener {
                capturedBitmap = null
                binding.ivCaptured.visibility = View.GONE
                binding.btnRetakePhoto.visibility = View.GONE
                binding.cameraPreview.visibility = View.VISIBLE
                binding.btnCapturePhoto.visibility = View.VISIBLE
                startCamera()
            }
        }

        binding.btnRegister.setOnClickListener {
            register()
        }

        binding.tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required for students", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCamera() {
        binding.cameraPreview.visibility = View.VISIBLE
        binding.btnCapturePhoto.visibility = View.VISIBLE

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }

            // FIXED: Changed to MAXIMIZE_QUALITY and added target rotation
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(Surface.ROTATION_0)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

                binding.btnCapturePhoto.setOnClickListener {
                    capturePhoto()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // FIXED: Properly convert ImageProxy to Bitmap with rotation correction
                    capturedBitmap = imageProxyToBitmap(image)
                    image.close()

                    binding.cameraPreview.visibility = View.GONE
                    binding.btnCapturePhoto.visibility = View.GONE

                    binding.ivCaptured.setImageBitmap(capturedBitmap)
                    binding.ivCaptured.visibility = View.VISIBLE
                    binding.btnRetakePhoto.visibility = View.VISIBLE
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Capture failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // FIXED: Rotate bitmap if needed (front camera often needs rotation)
        // Front camera images are usually mirrored and rotated
        val matrix = Matrix()
        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())

        // Mirror for front camera
        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)

        bitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )

        return bitmap
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

            if (capturedBitmap == null) {
                Toast.makeText(this, "Please capture your photo", Toast.LENGTH_SHORT).show()
                return false
            }
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
        // FIXED: Resize image before encoding to ensure it's not too large
        val resizedBitmap = resizeBitmap(capturedBitmap!!, 800, 800)
        val base64Image = bitmapToBase64(resizedBitmap)

        val encodingResult = faceRepository.encodeFace(base64Image)

        if (encodingResult.isSuccess) {
            val response = encodingResult.getOrNull()
            if (response != null && response.success) {
                val student = Student(
                    name = name,
                    email = email,
                    phone = phone,
                    rollNumber = rollNumber,
                    classrooms = emptyList(),
                    faceEncoding = response.encoding
                )

                val registerResult = repository.registerStudent(email, password, student)

                if (registerResult.isSuccess) {
                    showSuccess("Student registered successfully!")
                } else {
                    showError("Registration failed: ${registerResult.exceptionOrNull()?.message}")
                }
            } else {
                showError("Face encoding failed: ${response?.message ?: "Unknown error"}")
            }
        } else {
            showError("Face encoding error: ${encodingResult.exceptionOrNull()?.message}")
        }
    }

    // FIXED: Resize bitmap to reasonable size
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Calculate scale
        val scale = minOf(
            maxWidth.toFloat() / width,
            maxHeight.toFloat() / height,
            1.0f // Don't upscale
        )

        if (scale >= 1.0f) return bitmap

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // CRITICAL FIX: Changed Base64.DEFAULT to Base64.NO_WRAP
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Use higher quality (95) for face recognition
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        val byteArray = outputStream.toByteArray()
        // FIXED: Use NO_WRAP instead of DEFAULT to avoid line breaks
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
package com.example.classlogger.ui.student

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.classlogger.databinding.ActivityStudentAttendanceBinding
import com.example.classlogger.repository.FaceRecognitionRepository
import com.example.classlogger.repository.FirebaseRepository
import com.example.classlogger.utils.WiFiUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class StudentAttendanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentAttendanceBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var faceRepository: FaceRecognitionRepository
    private lateinit var wifiUtils: WiFiUtils

    private var studentId: String = ""
    private var sessionId: String = ""

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var capturedBitmap: Bitmap? = null

    private val CAMERA_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()
        faceRepository = FaceRecognitionRepository()
        wifiUtils = WiFiUtils(this)

        studentId = intent.getStringExtra("studentId") ?: ""
        sessionId = intent.getStringExtra("sessionId") ?: ""

        cameraExecutor = Executors.newSingleThreadExecutor()

        loadSessionDetails()
        setupUI()
    }

    private fun setupUI() {
        binding.btnCaptureSelfie.setOnClickListener {
            if (checkCameraPermission()) {
                captureSelfie()
            } else {
                requestCameraPermission()
            }
        }

        binding.btnRetake.setOnClickListener {
            capturedBitmap = null
            binding.imgCapturedSelfie.setImageBitmap(null)
            binding.imgCapturedSelfie.visibility = View.GONE
            binding.btnRetake.visibility = View.GONE
            binding.btnSubmit.visibility = View.GONE
            binding.btnCaptureSelfie.visibility = View.VISIBLE
            binding.cameraPreview.visibility = View.VISIBLE
            startCamera()
        }

        binding.btnSubmit.setOnClickListener {
            submitAttendance()
        }
    }

    private fun loadSessionDetails() {
        lifecycleScope.launch {
            binding.tvLoadingSession.text = "Loading session details..."

            // Get session and verify WiFi
            val sessionResult = repository.getSession(sessionId)

            if (sessionResult.isSuccess) {
                val session = sessionResult.getOrNull()
                if (session != null) {
                    val wifiVerified = wifiUtils.verifyClassroomWiFi(session.wifiSSID, session.wifiBSSID)
                    if (!wifiVerified) {
                        showWiFiError()
                        return@launch
                    }

                    binding.tvLoadingSession.text = "Session: ${session.subjectId}"

                    if (checkCameraPermission()) {
                        startCamera()
                    } else {
                        requestCameraPermission()
                    }
                } else {
                    Toast.makeText(this@StudentAttendanceActivity,
                        "Session not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } else {
                Toast.makeText(this@StudentAttendanceActivity,
                    "Error loading session: ${sessionResult.exceptionOrNull()?.message}",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showWiFiError() {
        MaterialAlertDialogBuilder(this)
            .setTitle("WiFi Error")
            .setMessage("You must be connected to the classroom WiFi to mark attendance.")
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
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
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Camera start failed: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureSelfie() {
        val imageCapture = imageCapture ?: return

        binding.btnCaptureSelfie.isEnabled = false

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    capturedBitmap = imageProxyToBitmap(image)
                    image.close()

                    binding.cameraPreview.visibility = View.GONE
                    binding.imgCapturedSelfie.setImageBitmap(capturedBitmap)
                    binding.imgCapturedSelfie.visibility = View.VISIBLE

                    binding.btnCaptureSelfie.visibility = View.GONE
                    binding.btnRetake.visibility = View.VISIBLE
                    binding.btnSubmit.visibility = View.VISIBLE
                    binding.btnSubmit.isEnabled = true
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@StudentAttendanceActivity,
                        "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                    binding.btnCaptureSelfie.isEnabled = true
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Compress to reduce size - 512x512 max, 80% quality
        val scaledBitmap = scaleBitmap(bitmap, 512, 512)
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        val scale = Math.min(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun submitAttendance() {
        binding.btnSubmit.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        val bitmap = capturedBitmap
        if (bitmap == null) {
            Toast.makeText(this, "No image captured", Toast.LENGTH_SHORT).show()
            binding.btnSubmit.isEnabled = true
            binding.progressBar.visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            try {
                // Convert bitmap to base64
                binding.tvStatus.text = "Processing image..."
                val base64Image = bitmapToBase64(bitmap)

                // Verify face with FaceNet server
                binding.tvStatus.text = "Verifying face..."
                val verificationResult = faceRepository.verifyFace(studentId, base64Image)

                // Check if the Result is successful
                if (verificationResult.isSuccess) {
                    // Safely get the response (nullable)
                    val response = verificationResult.getOrNull()

                    // Check if response exists
                    if (response != null) {
                        // Extract properties safely
                        val isMatchResult = response.isMatch
                        val confidenceScore = response.confidence

                        // Check if face matches with good confidence
                        if (isMatchResult && confidenceScore > 0.7f) {
                            // Face verified successfully, mark attendance
                            binding.tvStatus.text = "Saving attendance..."

                            val markResult = repository.markAttendance(
                                sessionId = sessionId,
                                studentId = studentId,
                                selfieBase64 = base64Image,
                                verificationScore = confidenceScore
                            )

                            if (markResult.isSuccess) {
                                binding.progressBar.visibility = View.GONE
                                showSuccessDialog()
                            } else {
                                val errorMessage = markResult.exceptionOrNull()?.message ?: "Unknown error"
                                showError("Failed to mark attendance: $errorMessage")
                            }
                        } else {
                            // Face doesn't match or confidence too low
                            val confidencePercent = String.format("%.2f", confidenceScore * 100)
                            showError("Face verification failed. Confidence: $confidencePercent%. Please try again.")
                        }
                    } else {
                        // Response is null - shouldn't happen but handle it
                        showError("Invalid response from verification server. Please try again.")
                    }
                } else {
                    // Verification request failed
                    val errorMessage = verificationResult.exceptionOrNull()?.message ?: "Unknown error"
                    showError("Verification error: $errorMessage")
                }
            } catch (e: Exception) {
                // Catch any unexpected errors
                showError("Error: ${e.message}")
            }
        }
    }

    private fun showSuccessDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Success!")
            .setMessage("Your attendance has been marked successfully.")
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.btnSubmit.isEnabled = true
        binding.tvStatus.text = ""

        MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
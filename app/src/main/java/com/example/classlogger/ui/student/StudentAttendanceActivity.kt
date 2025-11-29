package com.example.classlogger.ui.student

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Base64
import android.util.Log
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
import kotlinx.coroutines.tasks.await
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

    // Store session details for attendance marking
    private var classroomId: String = ""
    private var subjectId: String = ""
    private var subjectName: String = ""

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var capturedBitmap: Bitmap? = null

    private val CAMERA_PERMISSION_CODE = 100
    private val TAG = "StudentAttendanceActivity"
    private val CONFIDENCE_THRESHOLD = 0.7f

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
            if (checkCameraPermission()) captureSelfie()
            else requestCameraPermission()
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

        binding.btnSubmit.setOnClickListener { submitAttendance() }
    }

    private fun loadSessionDetails() {
        lifecycleScope.launch {
            binding.tvLoadingSession.text = "Loading session details..."

            val sessionResult = repository.getSession(sessionId)

            if (sessionResult.isSuccess) {
                val session = sessionResult.getOrNull()
                if (session != null) {

                    // Store session details for later use
                    classroomId = session.classroomId
                    subjectId = session.subjectId

                    // Fetch subject name from subjects collection
                    try {
                        val subjectDoc = repository.firestore
                            .collection("subjects")
                            .document(session.subjectId)
                            .get()
                            .await()

                        subjectName = subjectDoc.getString("name") ?: "Unknown Subject"
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching subject name: ${e.message}")
                        subjectName = "Unknown Subject"
                    }

                    val wifiVerified =
                        wifiUtils.verifyClassroomWiFi(session.wifiSSID, session.wifiBSSID)

                    if (!wifiVerified) {
                        showWiFiError()
                        return@launch
                    }

                    binding.tvLoadingSession.text = "Session: $subjectName"

                    if (checkCameraPermission()) startCamera()
                    else requestCameraPermission()

                } else {
                    Toast.makeText(
                        this@StudentAttendanceActivity,
                        "Session not found", Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } else {
                Toast.makeText(
                    this@StudentAttendanceActivity,
                    "Error loading session: ${sessionResult.exceptionOrNull()?.message}",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun showWiFiError() {
        MaterialAlertDialogBuilder(this)
            .setTitle("WiFi Error")
            .setMessage("You must be connected to the classroom WiFi to mark attendance.")
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                startCamera()
            else {
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
                .also { it.setSurfaceProvider(binding.cameraPreview.surfaceProvider) }

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(windowManager.defaultDisplay.rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Camera start failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
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

                    var bmp = imageProxyToBitmap(image)

                    // ------------------------------
                    // 🔧 ROTATION FIX APPLIED HERE
                    // ------------------------------
                    bmp = rotateBitmapIfNeeded(bmp, image)

                    capturedBitmap = bmp
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
                    Toast.makeText(
                        this@StudentAttendanceActivity,
                        "Capture failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.btnCaptureSelfie.isEnabled = true
                }
            }
        )
    }

    // ---------------------------------------------------------
    // ✔ ADDED FUNCTION: Rotate the bitmap using ImageProxy info
    // ---------------------------------------------------------
    private fun rotateBitmapIfNeeded(bitmap: Bitmap, image: ImageProxy): Bitmap {
        val rotationDegrees = image.imageInfo.rotationDegrees
        if (rotationDegrees == 0) return bitmap

        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())

        return Bitmap.createBitmap(
            bitmap,
            0, 0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        return when (image.format) {

            ImageFormat.JPEG -> {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }

            ImageFormat.YUV_420_888 -> {
                val yBuffer = image.planes[0].buffer
                val uBuffer = image.planes[1].buffer
                val vBuffer = image.planes[2].buffer

                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val nv21 = ByteArray(ySize + uSize + vSize)

                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)

                val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 95, out)

                BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
            }

            else -> {
                Log.e(TAG, "Unsupported ImageProxy format: ${image.format}")
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        return stream.toByteArray()
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        val scaled = scaleBitmap(bitmap, 720, 720)
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxWidth && height <= maxHeight) return bitmap

        val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        return Bitmap.createScaledBitmap(
            bitmap,
            (width * scale).toInt(),
            (height * scale).toInt(),
            true
        )
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
                binding.tvStatus.text = "Processing image..."
                val imageBytes = bitmapToJpegBytes(bitmap)

                binding.tvStatus.text = "Verifying face... (may take a moment)"
                val predictResult = faceRepository.predictFace(imageBytes)

                if (predictResult.isSuccess) {
                    val response = predictResult.getOrNull()

                    if (response != null) {

                        if (response.label == studentId && response.confidence >= CONFIDENCE_THRESHOLD) {

                            binding.tvStatus.text = "Saving attendance..."
                            val base64Image = bitmapToBase64(bitmap)

                            // Mark attendance with subject information
                            val mark = markAttendanceWithSubject(
                                sessionId,
                                studentId,
                                classroomId,
                                subjectId,
                                subjectName,
                                base64Image,
                                response.confidence
                            )

                            if (mark.isSuccess) {
                                binding.progressBar.visibility = View.GONE
                                showSuccessDialog()
                            } else {
                                showError("Failed to mark attendance.")
                            }

                        } else {
                            val reason = when (response.label) {
                                "no_face" -> "No face detected. Please ensure good lighting."
                                "unknown" -> "Face not recognized."
                                else -> "Face mismatch. Detected: ${response.label}"
                            }

                            showError("Face verification failed.\n$reason")
                        }
                    } else showError("Invalid response from server.")

                } else showError("Verification error: ${predictResult.exceptionOrNull()?.message}")

            } catch (e: Exception) {
                showError("Error: ${e.message}")
            }
        }
    }

    private suspend fun markAttendanceWithSubject(
        sessionId: String,
        studentId: String,
        classroomId: String,
        subjectId: String,
        subjectName: String,
        selfieBase64: String,
        verificationScore: Float
    ): Result<Unit> {
        return try {
            val attendanceData = hashMapOf(
                "sessionId" to sessionId,
                "studentId" to studentId,
                "classroomId" to classroomId,
                "subjectId" to subjectId,
                "subjectName" to subjectName,
                "status" to "PRESENT",
                "markedAt" to System.currentTimeMillis(),
                "markedBy" to "STUDENT",
                "selfieUrl" to selfieBase64,
                "verificationScore" to (verificationScore * 100).toInt(),
                "overriddenBy" to ""
            )

            repository.firestore
                .collection("attendance")
                .add(attendanceData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking attendance: ${e.message}")
            Result.failure(e)
        }
    }

    private fun showSuccessDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Success!")
            .setMessage("Your attendance has been marked successfully.")
            .setPositiveButton("OK") { _, _ -> finish() }
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
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
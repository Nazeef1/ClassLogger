package com.example.classlogger.ui.teacher

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.example.classlogger.databinding.ActivityTeacherCreateSessionBinding
import com.example.classlogger.models.Classroom
import com.example.classlogger.models.Subject
import com.example.classlogger.repository.FirebaseRepository
import com.example.classlogger.utils.WiFiUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TeacherCreateSessionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherCreateSessionBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var wifiUtils: WiFiUtils

    private var teacherId: String = ""
    private var selectedClassroom: Classroom? = null
    private var selectedSubject: Subject? = null
    private var startTime: Long = 0
    private var endTime: Long = 0

    // Permission launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }

        if (allGranted) {
            Log.d("WiFiPermission", "All permissions granted")
            checkLocationServicesAndDetectWiFi()
        } else {
            val deniedPermissions = permissions.filter { !it.value }.keys
            Log.e("WiFiPermission", "Denied permissions: $deniedPermissions")

            val shouldShowRationale = deniedPermissions.any { permission ->
                shouldShowRequestPermissionRationale(permission)
            }

            if (!shouldShowRationale) {
                showPermissionSettingsDialog()
            } else {
                showPermissionRationaleDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherCreateSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()
        wifiUtils = WiFiUtils(this)

        teacherId = intent.getStringExtra("teacherId") ?: ""

        setupUI()
        loadClassrooms()

        // Check permissions and WiFi
        checkPermissionsAndDetectWiFi()
    }

    private fun setupUI() {
        binding.btnSelectClassroom.setOnClickListener {
            showClassroomSelector()
        }

        binding.btnSelectSubject.setOnClickListener {
            if (selectedClassroom != null) {
                showSubjectSelector()
            } else {
                Toast.makeText(this, "Please select classroom first", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSelectStartTime.setOnClickListener {
            showTimePicker { time ->
                startTime = time
                binding.tvStartTime.text = formatTime(time)
            }
        }

        binding.btnSelectEndTime.setOnClickListener {
            showTimePicker { time ->
                endTime = time
                binding.tvEndTime.text = formatTime(time)
            }
        }

        binding.btnCreateSession.setOnClickListener {
            createSession()
        }

        // Display current date
        val dateFormat = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault())
        binding.tvCurrentDate.text = dateFormat.format(Date())
    }

    private fun checkPermissionsAndDetectWiFi() {
        Log.d("WiFiCheck", "Checking permissions...")

        if (!wifiUtils.hasRequiredPermissions()) {
            Log.d("WiFiCheck", "Permissions not granted, requesting...")
            requestLocationPermissions()
        } else {
            Log.d("WiFiCheck", "Permissions granted, checking location services...")
            checkLocationServicesAndDetectWiFi()
        }
    }

    private fun requestLocationPermissions() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Location Permission Required")
            .setMessage("ClassLogger needs location permission to detect which WiFi network you're connected to. This ensures sessions are created only when you're in the classroom.")
            .setPositiveButton("Grant Permission") { _, _ ->
                locationPermissionLauncher.launch(WiFiUtils.getRequiredPermissions())
            }
            .setNegativeButton("Cancel") { _, _ ->
                binding.tvWifiSSID.text = "WiFi: Permission denied"
                Toast.makeText(this, "Location permission is required to detect WiFi", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission Needed")
            .setMessage("Location permission is required to verify you're connected to the classroom WiFi. Without it, sessions cannot be created.")
            .setPositiveButton("Try Again") { _, _ ->
                locationPermissionLauncher.launch(WiFiUtils.getRequiredPermissions())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermissionSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission Required")
            .setMessage("Location permission is permanently denied. Please enable it in app settings:\n\nSettings → Apps → ClassLogger → Permissions → Location → Allow")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkLocationServicesAndDetectWiFi() {
        if (!isLocationEnabled()) {
            Log.e("WiFiCheck", "Location services disabled")
            showLocationServicesDialog()
        } else {
            Log.d("WiFiCheck", "Location services enabled, detecting WiFi...")
            detectWiFi()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    private fun showLocationServicesDialog() {
        binding.tvWifiSSID.text = "WiFi: Location services disabled"

        MaterialAlertDialogBuilder(this)
            .setTitle("Enable Location Services")
            .setMessage("Location services must be enabled to detect WiFi networks.\n\nPlease turn on location in your device settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun detectWiFi() {
        Log.d("WiFiCheck", "=== Attempting to detect WiFi ===")

        val wifiInfo = wifiUtils.getCurrentWiFiInfo()

        if (wifiInfo != null) {
            Log.d("WiFiCheck", "WiFi detected successfully")
            Log.d("WiFiCheck", "SSID: '${wifiInfo.ssid}'")
            Log.d("WiFiCheck", "BSSID: '${wifiInfo.bssid}'")

            binding.tvWifiSSID.text = "WiFi: ${wifiInfo.ssid}"
            binding.tvWifiBSSID.text = "BSSID: ${wifiInfo.bssid}"

            // Auto-select classroom based on WiFi
            autoSelectClassroomByWiFi(wifiInfo)
        } else {
            Log.e("WiFiCheck", "Failed to detect WiFi")
            Log.e("WiFiCheck", "Status: ${wifiUtils.getWiFiStatusMessage()}")

            binding.tvWifiSSID.text = wifiUtils.getWiFiStatusMessage()
            binding.tvWifiBSSID.text = ""

            Toast.makeText(this, wifiUtils.getWiFiStatusMessage(), Toast.LENGTH_LONG).show()
        }
    }

    private fun autoSelectClassroomByWiFi(wifiInfo: WiFiUtils.WiFiInfo) {
        Log.d("WiFiCheck", "=== Auto-selecting classroom ===")

        lifecycleScope.launch {
            repository.getTeacherClassrooms(teacherId).onSuccess { classrooms ->
                Log.d("WiFiCheck", "Found ${classrooms.size} classrooms")

                classrooms.forEach { classroom ->
                    Log.d("WiFiCheck", "Classroom: ${classroom.name}")
                    Log.d("WiFiCheck", "  Expected SSID: '${classroom.wifiSSID}'")
                    Log.d("WiFiCheck", "  Expected BSSID: '${classroom.wifiBSSID}'")
                }

                val matchingClassroom = classrooms.find { classroom ->
                    val ssidMatch = classroom.wifiSSID.trim().equals(wifiInfo.ssid.trim(), ignoreCase = true)
                    val bssidMatch = classroom.wifiBSSID.trim().lowercase() == wifiInfo.bssid.trim().lowercase()

                    Log.d("WiFiCheck", "Checking ${classroom.name}: SSID=$ssidMatch, BSSID=$bssidMatch")

                    ssidMatch && bssidMatch
                }

                if (matchingClassroom != null) {
                    Log.d("WiFiCheck", "✓ Matched classroom: ${matchingClassroom.name}")
                    selectedClassroom = matchingClassroom
                    binding.tvSelectedClassroom.text = matchingClassroom.name
                    loadSubjects(matchingClassroom.id)
                    Toast.makeText(
                        this@TeacherCreateSessionActivity,
                        "Auto-selected: ${matchingClassroom.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.e("WiFiCheck", "✗ No matching classroom found")
                    Toast.makeText(
                        this@TeacherCreateSessionActivity,
                        "No classroom matches this WiFi. Please select manually.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.onFailure { error ->
                Log.e("WiFiCheck", "Failed to load classrooms: ${error.message}")
                Toast.makeText(
                    this@TeacherCreateSessionActivity,
                    "Failed to load classrooms",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadClassrooms() {
        lifecycleScope.launch {
            repository.getTeacherClassrooms(teacherId).onSuccess { classrooms ->
                // Classrooms loaded successfully
                Log.d("WiFiCheck", "Loaded ${classrooms.size} classrooms")
            }.onFailure {
                Toast.makeText(this@TeacherCreateSessionActivity,
                    "Failed to load classrooms", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showClassroomSelector() {
        lifecycleScope.launch {
            repository.getTeacherClassrooms(teacherId).onSuccess { classrooms ->
                val classroomNames = classrooms.map { it.name }.toTypedArray()

                MaterialAlertDialogBuilder(this@TeacherCreateSessionActivity)
                    .setTitle("Select Classroom")
                    .setItems(classroomNames) { _, which ->
                        selectedClassroom = classrooms[which]
                        binding.tvSelectedClassroom.text = classrooms[which].name
                        loadSubjects(classrooms[which].id)
                    }
                    .show()
            }
        }
    }

    private fun loadSubjects(classroomId: String) {
        lifecycleScope.launch {
            repository.getClassroomSubjects(classroomId).onSuccess { subjects ->
                // Subjects loaded for selection
                Log.d("WiFiCheck", "Loaded ${subjects.size} subjects")
            }
        }
    }

    private fun showSubjectSelector() {
        selectedClassroom?.let { classroom ->
            lifecycleScope.launch {
                repository.getClassroomSubjects(classroom.id).onSuccess { subjects ->
                    if (subjects.isEmpty()) {
                        Toast.makeText(
                            this@TeacherCreateSessionActivity,
                            "No subjects found for this classroom",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@onSuccess
                    }

                    val subjectNames = subjects.map { "${it.name} (${it.code})" }.toTypedArray()

                    MaterialAlertDialogBuilder(this@TeacherCreateSessionActivity)
                        .setTitle("Select Subject")
                        .setItems(subjectNames) { _, which ->
                            selectedSubject = subjects[which]
                            binding.tvSelectedSubject.text = subjectNames[which]
                        }
                        .show()
                }
            }
        }
    }

    private fun showTimePicker(onTimeSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val timeCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, selectedHour)
                set(Calendar.MINUTE, selectedMinute)
                set(Calendar.SECOND, 0)
            }
            onTimeSelected(timeCalendar.timeInMillis)
        }, hour, minute, false).show()
    }

    private fun formatTime(timeMillis: Long): String {
        val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return format.format(Date(timeMillis))
    }

    private fun createSession() {
        Log.d("SessionCreate", "=== Creating Session ===")

        // Validation
        if (selectedClassroom == null) {
            Toast.makeText(this, "Please select a classroom", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedSubject == null) {
            Toast.makeText(this, "Please select a subject", Toast.LENGTH_SHORT).show()
            return
        }

        if (startTime == 0L || endTime == 0L) {
            Toast.makeText(this, "Please select start and end time", Toast.LENGTH_SHORT).show()
            return
        }

        if (endTime <= startTime) {
            Toast.makeText(this, "End time must be after start time", Toast.LENGTH_SHORT).show()
            return
        }

        // Re-check WiFi before creating session
        val wifiInfo = wifiUtils.getCurrentWiFiInfo()
        if (wifiInfo == null) {
            Toast.makeText(this, "Please connect to classroom WiFi", Toast.LENGTH_SHORT).show()
            return
        }

        // Verify WiFi matches selected classroom
        val isCorrectWiFi = wifiUtils.verifyClassroomWiFi(
            selectedClassroom!!.wifiSSID,
            selectedClassroom!!.wifiBSSID
        )

        if (!isCorrectWiFi) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Wrong WiFi Network")
                .setMessage("You're connected to '${wifiInfo.ssid}'\n\nPlease connect to '${selectedClassroom!!.wifiSSID}' to create a session for this classroom.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        Log.d("SessionCreate", "WiFi verified, creating session...")

        // Create session
        binding.btnCreateSession.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            repository.createSession(
                teacherId = teacherId,
                subjectId = selectedSubject!!.id,
                classroomId = selectedClassroom!!.id,
                startTime = startTime,
                endTime = endTime,
                wifiSSID = wifiInfo.ssid,
                wifiBSSID = wifiInfo.bssid
            ).onSuccess { sessionId ->
                Log.d("SessionCreate", "✓ Session created: $sessionId")
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@TeacherCreateSessionActivity,
                    "Session created successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure { error ->
                Log.e("SessionCreate", "✗ Failed to create session: ${error.message}")
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@TeacherCreateSessionActivity,
                    "Failed to create session: ${error.message}", Toast.LENGTH_SHORT).show()
                binding.btnCreateSession.isEnabled = true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check WiFi when returning from settings
        if (wifiUtils.hasRequiredPermissions() && isLocationEnabled()) {
            detectWiFi()
        }
    }
}
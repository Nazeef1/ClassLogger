package com.example.classlogger.ui.teacher

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.classlogger.databinding.ActivityTeacherViewAttendanceBinding
import com.example.classlogger.models.AttendanceRecord
import com.example.classlogger.models.AttendanceStatus
import com.example.classlogger.models.Classroom
import com.example.classlogger.models.Student
import com.example.classlogger.repository.FirebaseRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.classlogger.R
import java.text.SimpleDateFormat
import java.util.*

data class AttendanceStats(
    val totalSessions: Int,
    val attended: Int,
    val absent: Int,
    val percentage: Float
)

class TeacherViewAttendanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherViewAttendanceBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var attendanceAdapter: AttendanceAdapter

    private var teacherId: String = ""
    private var selectedClassroom: Classroom? = null
    private var selectedSubjectId: String? = null
    private var selectedSubjectName: String? = null
    private var selectedStudent: Student? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherViewAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()
        teacherId = intent.getStringExtra("teacherId") ?: ""

        setupRecyclerView()
        setupUI()
        loadClassrooms()
    }

    private fun setupRecyclerView() {
        attendanceAdapter = AttendanceAdapter(emptyList())
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@TeacherViewAttendanceActivity)
            adapter = attendanceAdapter
        }
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

        binding.btnSelectStudent.setOnClickListener {
            if (selectedClassroom != null && selectedSubjectId != null) {
                showStudentSelector()
            } else {
                Toast.makeText(this, "Please select classroom and subject first", Toast.LENGTH_SHORT).show()
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

                        // Reset subject and student selection
                        selectedSubjectId = null
                        selectedSubjectName = null
                        binding.tvSelectedSubject.text = "No subject selected"

                        selectedStudent = null
                        binding.tvSelectedStudent.text = "No student selected"

                        clearAttendanceData()
                    }
                    .show()
            }
        }
    }

    private fun showSubjectSelector() {
        selectedClassroom?.let { classroom ->
            lifecycleScope.launch {
                try {
                    // Get all subjects for this classroom
                    val subjectIds = classroom.subjects
                    val subjects = mutableListOf<Pair<String, String>>() // id, name

                    for (subjectId in subjectIds) {
                        val subjectDoc = repository.firestore
                            .collection("subjects")
                            .document(subjectId)
                            .get()
                            .await()

                        if (subjectDoc.exists()) {
                            val name = subjectDoc.getString("name") ?: ""
                            val code = subjectDoc.getString("code") ?: ""
                            subjects.add(Pair(subjectId, "$name ($code)"))
                        }
                    }

                    val subjectNames = subjects.map { it.second }.toTypedArray()

                    MaterialAlertDialogBuilder(this@TeacherViewAttendanceActivity)
                        .setTitle("Select Subject")
                        .setItems(subjectNames) { _, which ->
                            selectedSubjectId = subjects[which].first
                            selectedSubjectName = subjects[which].second
                            binding.tvSelectedSubject.text = subjects[which].second

                            // Reset student selection
                            selectedStudent = null
                            binding.tvSelectedStudent.text = "No student selected"

                            clearAttendanceData()
                        }
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@TeacherViewAttendanceActivity,
                        "Error loading subjects: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
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
                            loadAttendanceHistory()
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

    private fun loadAttendanceHistory() {
        val classroom = selectedClassroom
        val student = selectedStudent
        val subjectId = selectedSubjectId

        if (classroom == null || student == null || subjectId == null) {
            return
        }

        lifecycleScope.launch {
            try {
                showLoading(true)

                // Get all sessions for this classroom and subject
                val sessionsSnapshot = repository.firestore
                    .collection("sessions")
                    .whereEqualTo("classroomId", classroom.id)
                    .whereEqualTo("subjectId", subjectId)
                    .get()
                    .await()

                val records = mutableListOf<AttendanceRecord>()

                for (sessionDoc in sessionsSnapshot.documents) {
                    val sessionId = sessionDoc.id

                    // Get subject name from session or fetch it
                    val subjectName = sessionDoc.getString("subjectName") ?: run {
                        val subjectDoc = repository.firestore
                            .collection("subjects")
                            .document(subjectId)
                            .get()
                            .await()
                        subjectDoc.getString("name") ?: "Unknown"
                    }

                    val startTime = sessionDoc.getLong("startTime") ?: 0
                    val endTime = sessionDoc.getLong("endTime") ?: 0
                    val date = sessionDoc.getString("date") ?: ""

                    // Check if there's an attendance record for this student in this session
                    val attendanceSnapshot = repository.firestore
                        .collection("attendance")
                        .whereEqualTo("sessionId", sessionId)
                        .whereEqualTo("studentId", student.id)
                        .get()
                        .await()

                    if (attendanceSnapshot.documents.isNotEmpty()) {
                        // Student has an attendance record
                        val attendanceDoc = attendanceSnapshot.documents[0]
                        val statusString = attendanceDoc.getString("status") ?: "ABSENT"
                        val status = try {
                            AttendanceStatus.valueOf(statusString)
                        } catch (e: Exception) {
                            AttendanceStatus.ABSENT
                        }

                        val record = AttendanceRecord(
                            sessionId = sessionId,
                            subjectName = subjectName,
                            date = date,
                            startTime = startTime,
                            endTime = endTime,
                            status = status,
                            markedAt = attendanceDoc.getLong("markedAt") ?: 0,
                            selfieUrl = attendanceDoc.getString("selfieUrl") ?: ""
                        )
                        records.add(record)
                    } else {
                        // No attendance record means student was ABSENT
                        val record = AttendanceRecord(
                            sessionId = sessionId,
                            subjectName = subjectName,
                            date = date,
                            startTime = startTime,
                            endTime = endTime,
                            status = AttendanceStatus.ABSENT,
                            markedAt = 0,
                            selfieUrl = ""
                        )
                        records.add(record)
                    }
                }

                // Sort by startTime (newest first)
                records.sortByDescending { it.startTime }

                // Calculate statistics
                val stats = calculateStats(records)

                // Update UI
                displayAttendanceData(records, stats)

                showLoading(false)

            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                    this@TeacherViewAttendanceActivity,
                    "Error loading attendance: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun calculateStats(records: List<AttendanceRecord>): AttendanceStats {
        val totalSessions = records.size
        val attended = records.count { it.status == AttendanceStatus.PRESENT }
        val absent = totalSessions - attended
        val percentage = if (totalSessions > 0) {
            (attended.toFloat() / totalSessions.toFloat()) * 100
        } else {
            0f
        }

        return AttendanceStats(
            totalSessions = totalSessions,
            attended = attended,
            absent = absent,
            percentage = percentage
        )
    }

    private fun displayAttendanceData(records: List<AttendanceRecord>, stats: AttendanceStats) {
        if (records.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.tvNoRecords.visibility = View.VISIBLE
            binding.cardStats.visibility = View.GONE
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.tvNoRecords.visibility = View.GONE
            binding.cardStats.visibility = View.VISIBLE

            // Update adapter
            attendanceAdapter.updateRecords(records)

            // Update stats
            binding.tvTotalSessions.text = "Total: ${stats.totalSessions}"
            binding.tvAttended.text = "Present: ${stats.attended}"
            binding.tvAbsent.text = "Absent: ${stats.absent}"
            binding.tvPercentage.text = "Attendance: ${String.format("%.1f", stats.percentage)}%"
        }
    }

    private fun clearAttendanceData() {
        attendanceAdapter.updateRecords(emptyList())
        binding.cardStats.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.tvNoRecords.visibility = View.GONE
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
}

// AttendanceAdapter
class AttendanceAdapter(private var records: List<AttendanceRecord>) :
    RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    class AttendanceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvSubject: TextView = view.findViewById(R.id.tvSubject)
        val tvSessionTime: TextView = view.findViewById(R.id.tvSessionTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_record, parent, false)
        return AttendanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        val record = records[position]

        // Show the date from the date field or fallback to markedAt
        val displayDate = if (record.date.isNotEmpty()) record.date else {
            if (record.markedAt > 0) dateFormat.format(Date(record.markedAt)) else "N/A"
        }
        holder.tvDate.text = displayDate

        // Show marked time
        if (record.markedAt > 0) {
            holder.tvTime.text = "Marked: ${timeFormat.format(Date(record.markedAt))}"
        } else {
            holder.tvTime.text = "Not marked"
        }

        // Show status
        holder.tvStatus.text = record.status.name

        // Set status color
        val statusColor = when (record.status) {
            AttendanceStatus.PRESENT ->
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark)
            AttendanceStatus.ABSENT ->
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark)
        }
        holder.tvStatus.setTextColor(statusColor)

        // Show subject name
        holder.tvSubject.text = "Subject: ${record.subjectName}"

        // Show session time range
        if (record.startTime > 0 && record.endTime > 0) {
            val startTimeStr = timeFormat.format(Date(record.startTime))
            val endTimeStr = timeFormat.format(Date(record.endTime))
            holder.tvSessionTime.text = "Session: $startTimeStr - $endTimeStr"
            holder.tvSessionTime.visibility = View.VISIBLE
        } else {
            holder.tvSessionTime.visibility = View.GONE
        }
    }

    override fun getItemCount() = records.size

    fun updateRecords(newRecords: List<AttendanceRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }
}
package com.example.classlogger.ui.student

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.classlogger.databinding.ActivityStudentSubjectDetailBinding
import com.example.classlogger.models.AttendanceRecord
import com.example.classlogger.models.AttendanceStatus
import com.example.classlogger.repository.FirebaseRepository
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


class StudentSubjectDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentSubjectDetailBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var adapter: StudentAttendanceDetailAdapter

    private var studentId: String = ""
    private var subjectName: String = ""
    private var subjectCode: String = ""
    private var subjectId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentSubjectDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()
        studentId = intent.getStringExtra("studentId") ?: ""
        subjectName = intent.getStringExtra("subjectName") ?: ""
        subjectCode = intent.getStringExtra("subjectCode") ?: ""

        setupToolbar()
        setupRecyclerView()
        loadAttendanceDetails()

        binding.swipeRefresh.setOnRefreshListener {
            loadAttendanceDetails()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.title = subjectName
        binding.toolbar.subtitle = subjectCode
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = StudentAttendanceDetailAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadAttendanceDetails() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // First, get the subjectId from subjectName
                val subjectsSnapshot = repository.firestore
                    .collection("subjects")
                    .whereEqualTo("name", subjectName)
                    .limit(1)
                    .get()
                    .await()

                if (subjectsSnapshot.documents.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    binding.tvNoRecords.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                    binding.cardStats.visibility = View.GONE
                    Toast.makeText(
                        this@StudentSubjectDetailActivity,
                        "Subject not found",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                subjectId = subjectsSnapshot.documents[0].id

                // Get all sessions for this subject across all student's classrooms
                val studentDoc = repository.firestore
                    .collection("students")
                    .document(studentId)
                    .get()
                    .await()

                val classroomIds = studentDoc.get("classrooms") as? List<String> ?: emptyList()

                val allRecords = mutableListOf<AttendanceRecord>()

                // For each classroom, get sessions for this subject using subjectId
                for (classroomId in classroomIds) {
                    val sessionsSnapshot = repository.firestore
                        .collection("sessions")
                        .whereEqualTo("classroomId", classroomId)
                        .whereEqualTo("subjectId", subjectId)
                        .get()
                        .await()

                    for (sessionDoc in sessionsSnapshot.documents) {
                        val sessionId = sessionDoc.id
                        val startTime = sessionDoc.getLong("startTime") ?: 0
                        val endTime = sessionDoc.getLong("endTime") ?: 0
                        val date = sessionDoc.getString("date") ?: ""

                        // Check if student has attendance record for this session
                        val attendanceSnapshot = repository.firestore
                            .collection("attendance")
                            .whereEqualTo("sessionId", sessionId)
                            .whereEqualTo("studentId", studentId)
                            .get()
                            .await()

                        if (attendanceSnapshot.documents.isNotEmpty()) {
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
                            allRecords.add(record)
                        } else {
                            // No record means absent
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
                            allRecords.add(record)
                        }
                    }
                }

                // Sort by start time (newest first)
                allRecords.sortByDescending { it.startTime }

                // Calculate stats
                val totalClasses = allRecords.size
                val attendedClasses = allRecords.count { it.status == AttendanceStatus.PRESENT }
                val percentage = if (totalClasses > 0) {
                    (attendedClasses.toFloat() / totalClasses.toFloat()) * 100
                } else {
                    0f
                }

                // Update UI
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                if (allRecords.isEmpty()) {
                    binding.tvNoRecords.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                    binding.cardStats.visibility = View.GONE
                } else {
                    binding.tvNoRecords.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    binding.cardStats.visibility = View.VISIBLE

                    adapter.submitList(allRecords)

                    // Update stats
                    binding.tvTotalClasses.text = "Total: $totalClasses"
                    binding.tvAttendedClasses.text = "Present: $attendedClasses"
                    binding.tvAbsentClasses.text = "Absent: ${totalClasses - attendedClasses}"
                    binding.tvPercentage.text = String.format("%.1f%%", percentage)

                    // Update percentage color
                    val color = when {
                        percentage >= 75 -> android.graphics.Color.parseColor("#4CAF50")
                        percentage >= 50 -> android.graphics.Color.parseColor("#FFC107")
                        else -> android.graphics.Color.parseColor("#F44336")
                    }
                    binding.tvPercentage.setTextColor(color)
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(
                    this@StudentSubjectDetailActivity,
                    "Error loading attendance: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}


class StudentAttendanceDetailAdapter : RecyclerView.Adapter<StudentAttendanceDetailAdapter.ViewHolder>() {

    private var records = listOf<AttendanceRecord>()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    fun submitList(newRecords: List<AttendanceRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_attendance_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount() = records.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvMarkedAt: TextView = itemView.findViewById(R.id.tvMarkedAt)

        fun bind(record: AttendanceRecord) {
            // Date
            tvDate.text = if (record.date.isNotEmpty()) {
                record.date
            } else {
                dateFormat.format(Date(record.startTime))
            }

            // Session time
            if (record.startTime > 0 && record.endTime > 0) {
                val startTimeStr = timeFormat.format(Date(record.startTime))
                val endTimeStr = timeFormat.format(Date(record.endTime))
                tvTime.text = "$startTimeStr - $endTimeStr"
            } else {
                tvTime.text = "Time not available"
            }

            // Status
            tvStatus.text = record.status.name
            val statusColor = when (record.status) {
                AttendanceStatus.PRESENT -> ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark)
                AttendanceStatus.ABSENT -> ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark)
            }
            tvStatus.setTextColor(statusColor)

            // Marked at
            if (record.markedAt > 0) {
                tvMarkedAt.text = "Marked at: ${timeFormat.format(Date(record.markedAt))}"
                tvMarkedAt.visibility = View.VISIBLE
            } else {
                tvMarkedAt.visibility = View.GONE
            }
        }
    }
}
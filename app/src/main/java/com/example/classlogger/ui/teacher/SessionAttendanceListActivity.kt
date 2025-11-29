package com.example.classlogger.ui.teacher

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.classlogger.R
import com.example.classlogger.databinding.ActivitySessionAttendanceListBinding
import com.example.classlogger.models.Attendance
import com.example.classlogger.models.AttendanceStatus
import com.example.classlogger.models.Student
import com.example.classlogger.repository.FirebaseRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SessionAttendanceListActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySessionAttendanceListBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var adapter: SessionAttendanceAdapter

    private var sessionId: String = ""
    private var teacherId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionAttendanceListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()
        sessionId = intent.getStringExtra("sessionId") ?: ""
        teacherId = intent.getStringExtra("teacherId") ?: ""

        setupToolbar()
        setupRecyclerView()
        loadAttendance()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = SessionAttendanceAdapter(
            onViewSelfie = { attendance ->
                if (attendance.selfieUrl.isNotEmpty()) {
                    showSelfieDialog(attendance.selfieUrl)
                }
            },
            onToggleAttendance = { student, attendance ->
                toggleAttendance(student, attendance)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadAttendance() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            repository.getSessionAttendance(sessionId)
                .onSuccess { attendanceList ->
                    binding.progressBar.visibility = View.GONE

                    if (attendanceList.isEmpty()) {
                        binding.tvNoData.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE
                    } else {
                        binding.tvNoData.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                        adapter.submitList(attendanceList)

                        // Update statistics
                        val presentCount = attendanceList.count { it.second.status == AttendanceStatus.PRESENT }
                        val totalCount = attendanceList.size
                        binding.tvStats.text = "Present: $presentCount / $totalCount"
                    }
                }
                .onFailure { error ->
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@SessionAttendanceListActivity,
                        "Error: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun showSelfieDialog(base64Image: String) {
        try {
            val dialogView = layoutInflater.inflate(R.layout.dialog_selfie_view, null)
            val imageView = dialogView.findViewById<ImageView>(R.id.ivSelfie)

            // Decode Base64 to Bitmap
            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            imageView.setImageBitmap(bitmap)

            MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error displaying image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleAttendance(student: Student, attendance: Attendance) {
        val newStatus = if (attendance.status == AttendanceStatus.PRESENT) {
            AttendanceStatus.ABSENT
        } else {
            AttendanceStatus.PRESENT
        }

        lifecycleScope.launch {
            repository.overrideAttendance(sessionId, student.id, newStatus, teacherId)
                .onSuccess {
                    Toast.makeText(
                        this@SessionAttendanceListActivity,
                        "Attendance updated",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadAttendance()
                }
                .onFailure { error ->
                    Toast.makeText(
                        this@SessionAttendanceListActivity,
                        "Error: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
}

// ADAPTER IN SAME FILE
class SessionAttendanceAdapter(
    private val onViewSelfie: (Attendance) -> Unit,
    private val onToggleAttendance: (Student, Attendance) -> Unit
) : RecyclerView.Adapter<SessionAttendanceAdapter.ViewHolder>() {

    private var attendanceList = listOf<Pair<Student, Attendance>>()

    fun submitList(newList: List<Pair<Student, Attendance>>) {
        attendanceList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(attendanceList[position])
    }

    override fun getItemCount() = attendanceList.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvStudentName)
        private val tvRollNumber: TextView = itemView.findViewById(R.id.tvRollNumber)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val btnViewSelfie: ImageButton = itemView.findViewById(R.id.btnViewSelfie)
        private val btnToggle: ImageButton = itemView.findViewById(R.id.btnToggle)

        fun bind(item: Pair<Student, Attendance>) {
            val student = item.first
            val attendance = item.second

            tvName.text = student.name
            tvRollNumber.text = "Roll No: ${student.rollNumber}"

            if (attendance.status == AttendanceStatus.PRESENT) {
                tvStatus.text = "PRESENT"
                tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                itemView.setBackgroundColor(Color.parseColor("#E8F5E9"))
            } else {
                tvStatus.text = "ABSENT"
                tvStatus.setTextColor(Color.parseColor("#F44336"))
                itemView.setBackgroundColor(Color.parseColor("#FFEBEE"))
            }

            // Show view selfie button only if selfie exists
            if (attendance.selfieUrl.isNotEmpty()) {
                btnViewSelfie.visibility = View.VISIBLE
                btnViewSelfie.setOnClickListener {
                    onViewSelfie(attendance)
                }
            } else {
                btnViewSelfie.visibility = View.GONE
            }

            btnToggle.setOnClickListener {
                onToggleAttendance(student, attendance)
            }
        }
    }
}
package com.example.classlogger.ui.student

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.classlogger.R
import com.example.classlogger.databinding.ActivityStudentAttendanceHistoryBinding
import com.example.classlogger.models.SubjectAttendance
import com.example.classlogger.repository.FirebaseRepository
import kotlinx.coroutines.launch

class StudentAttendanceHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentAttendanceHistoryBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var adapter: SubjectAttendanceAdapter

    private var studentId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentAttendanceHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()
        studentId = intent.getStringExtra("studentId") ?: ""

        setupToolbar()
        setupRecyclerView()
        loadSubjectAttendance()

        binding.swipeRefresh.setOnRefreshListener {
            loadSubjectAttendance()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = SubjectAttendanceAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadSubjectAttendance() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            repository.getStudentSubjects(studentId)
                .onSuccess { subjects ->
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false

                    if (subjects.isEmpty()) {
                        binding.tvNoSubjects.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE
                    } else {
                        binding.tvNoSubjects.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                        adapter.submitList(subjects)
                    }
                }
                .onFailure { error ->
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(
                        this@StudentAttendanceHistoryActivity,
                        "Failed to load attendance: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
}

class SubjectAttendanceAdapter : RecyclerView.Adapter<SubjectAttendanceAdapter.ViewHolder>() {

    private var subjects = listOf<SubjectAttendance>()

    fun submitList(newSubjects: List<SubjectAttendance>) {
        subjects = newSubjects
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subject_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(subjects[position])
    }

    override fun getItemCount() = subjects.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSubjectName: TextView = itemView.findViewById(R.id.tvSubjectName)
        private val tvSubjectCode: TextView = itemView.findViewById(R.id.tvSubjectCode)
        private val tvAttendedClasses: TextView = itemView.findViewById(R.id.tvAttendedClasses)
        private val tvTotalClasses: TextView = itemView.findViewById(R.id.tvTotalClasses)
        private val tvPercentage: TextView = itemView.findViewById(R.id.tvPercentage)
        private val progressBar: View = itemView.findViewById(R.id.progressBar)

        fun bind(subject: SubjectAttendance) {
            tvSubjectName.text = subject.subjectName
            tvSubjectCode.text = subject.subjectCode
            tvAttendedClasses.text = subject.attendedClasses.toString()
            tvTotalClasses.text = "/ ${subject.totalClasses}"
            tvPercentage.text = String.format("%.1f%%", subject.attendancePercentage)

            // Update progress bar width
            progressBar.post {
                val layoutParams = progressBar.layoutParams
                layoutParams.width = (itemView.width * (subject.attendancePercentage / 100)).toInt()
                progressBar.layoutParams = layoutParams
            }

            // Change color based on percentage
            val color = when {
                subject.attendancePercentage >= 75 -> android.graphics.Color.parseColor("#4CAF50")
                subject.attendancePercentage >= 50 -> android.graphics.Color.parseColor("#FFC107")
                else -> android.graphics.Color.parseColor("#F44336")
            }
            progressBar.setBackgroundColor(color)
            tvPercentage.setTextColor(color)
        }
    }
}
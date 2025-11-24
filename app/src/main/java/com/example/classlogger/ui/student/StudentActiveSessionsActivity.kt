package com.example.classlogger.ui.student

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.classlogger.R
import com.example.classlogger.databinding.ActivityStudentActiveSessionsBinding
import com.example.classlogger.models.ActiveSession
import com.example.classlogger.repository.FirebaseRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class StudentActiveSessionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentActiveSessionsBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var adapter: ActiveSessionAdapter

    private var studentId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentActiveSessionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()
        studentId = intent.getStringExtra("studentId") ?: ""

        setupRecyclerView()
        loadActiveSessions()

        binding.swipeRefresh.setOnRefreshListener {
            loadActiveSessions()
        }
    }

    private fun setupRecyclerView() {
        adapter = ActiveSessionAdapter { activeSession ->
            val intent = Intent(this, StudentAttendanceActivity::class.java).apply {
                putExtra("studentId", studentId)
                putExtra("sessionId", activeSession.sessionId)
            }
            startActivity(intent)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadActiveSessions() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            repository.getActiveSessionsForStudent(studentId)
                .onSuccess { sessions ->
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false

                    if (sessions.isEmpty()) {
                        binding.tvNoSessions.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE
                    } else {
                        binding.tvNoSessions.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                        adapter.submitList(sessions)
                    }
                }
                .onFailure { error ->
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(
                        this@StudentActiveSessionsActivity,
                        "Failed to load sessions: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
}

class ActiveSessionAdapter(
    private val onSessionClick: (ActiveSession) -> Unit
) : RecyclerView.Adapter<ActiveSessionAdapter.ViewHolder>() {

    private var sessions = listOf<ActiveSession>()

    fun submitList(newSessions: List<ActiveSession>) {
        sessions = newSessions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_active_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount() = sessions.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSubjectName: TextView = itemView.findViewById(R.id.tvSubjectName)
        private val tvTeacherName: TextView = itemView.findViewById(R.id.tvTeacherName)
        private val tvClassroom: TextView = itemView.findViewById(R.id.tvClassroom)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val btnMarkAttendance: Button = itemView.findViewById(R.id.btnMarkAttendance)

        fun bind(session: ActiveSession) {
            tvSubjectName.text = session.subjectName
            tvTeacherName.text = "Teacher: ${session.teacherName}"
            tvClassroom.text = "Room: ${session.classroomName}"

            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val startTime = timeFormat.format(Date(session.startTime))
            val endTime = timeFormat.format(Date(session.endTime))
            tvTime.text = "$startTime - $endTime"

            btnMarkAttendance.setOnClickListener {
                onSessionClick(session)
            }
        }
    }
}
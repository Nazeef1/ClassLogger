package com.example.classlogger.ui.teacher

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
import com.example.classlogger.databinding.ActivityTeacherActiveSessionsBinding
import com.example.classlogger.models.Session
import com.example.classlogger.models.SessionStatus
import com.example.classlogger.repository.FirebaseRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class TeacherActiveSessionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherActiveSessionsBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var adapter: TeacherActiveSessionAdapter

    private var teacherId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherActiveSessionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository()
        teacherId = intent.getStringExtra("teacherId") ?: ""

        setupToolbar()
        setupRecyclerView()
        loadActiveSessions()

        binding.swipeRefresh.setOnRefreshListener {
            loadActiveSessions()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = TeacherActiveSessionAdapter(
            onViewAttendance = { session ->
                viewSessionAttendance(session)
            },
            onCloseSession = { session ->
                confirmCloseSession(session)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadActiveSessions() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val activeSessions = mutableListOf<Session>()

                val sessionsQuery = repository.firestore
                    .collection("sessions")
                    .whereEqualTo("teacherId", teacherId)
                    .whereEqualTo("status", SessionStatus.ACTIVE.name)
                    .get()
                    .await()

                for (doc in sessionsQuery.documents) {
                    val session = Session(
                        id = doc.id,
                        teacherId = doc.getString("teacherId") ?: "",
                        subjectId = doc.getString("subjectId") ?: "",
                        classroomId = doc.getString("classroomId") ?: "",
                        date = doc.getString("date") ?: "",
                        startTime = doc.getLong("startTime") ?: 0,
                        endTime = doc.getLong("endTime") ?: 0,
                        status = SessionStatus.valueOf(doc.getString("status") ?: "ACTIVE"),
                        wifiSSID = doc.getString("wifiSSID") ?: "",
                        wifiBSSID = doc.getString("wifiBSSID") ?: "",
                        attendanceWindow = doc.getBoolean("attendanceWindow") ?: false
                    )
                    activeSessions.add(session)
                }

                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                if (activeSessions.isEmpty()) {
                    binding.tvNoSessions.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.tvNoSessions.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    adapter.submitList(activeSessions)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(
                    this@TeacherActiveSessionsActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun viewSessionAttendance(session: Session) {
        val intent = Intent(this, SessionAttendanceListActivity::class.java)
        intent.putExtra("sessionId", session.id)
        intent.putExtra("teacherId", teacherId)
        startActivity(intent)
    }

    private fun confirmCloseSession(session: Session) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Close Session")
            .setMessage("Are you sure you want to close this session? Students will no longer be able to mark attendance.")
            .setPositiveButton("Close") { _, _ ->
                closeSession(session)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun closeSession(session: Session) {
        lifecycleScope.launch {
            repository.closeSession(session.id)
                .onSuccess {
                    Toast.makeText(
                        this@TeacherActiveSessionsActivity,
                        "Session closed successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadActiveSessions()
                }
                .onFailure { error ->
                    Toast.makeText(
                        this@TeacherActiveSessionsActivity,
                        "Failed to close session: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    override fun onResume() {
        super.onResume()
        loadActiveSessions()
    }
}

class TeacherActiveSessionAdapter(
    private val onViewAttendance: (Session) -> Unit,
    private val onCloseSession: (Session) -> Unit
) : RecyclerView.Adapter<TeacherActiveSessionAdapter.ViewHolder>() {

    private var sessions = listOf<Session>()

    fun submitList(newSessions: List<Session>) {
        sessions = newSessions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_teacher_active_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount() = sessions.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSubject: TextView = itemView.findViewById(R.id.tvSubject)
        private val tvClassroom: TextView = itemView.findViewById(R.id.tvClassroom)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val btnViewAttendance: Button = itemView.findViewById(R.id.btnViewAttendance)
        private val btnCloseSession: Button = itemView.findViewById(R.id.btnCloseSession)

        fun bind(session: Session) {
            tvSubject.text = "Subject ID: ${session.subjectId}"
            tvClassroom.text = "Classroom ID: ${session.classroomId}"
            tvDate.text = session.date

            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            tvTime.text = "${timeFormat.format(Date(session.startTime))} - ${timeFormat.format(Date(session.endTime))}"

            tvStatus.text = if (session.attendanceWindow) "Active" else "Window Closed"
            tvStatus.setTextColor(
                if (session.attendanceWindow)
                    android.graphics.Color.parseColor("#4CAF50")
                else
                    android.graphics.Color.parseColor("#F44336")
            )

            btnViewAttendance.setOnClickListener {
                onViewAttendance(session)
            }

            btnCloseSession.setOnClickListener {
                onCloseSession(session)
            }
        }
    }
}
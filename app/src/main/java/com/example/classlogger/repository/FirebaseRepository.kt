package com.example.classlogger.repository

import com.example.classlogger.models.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class FirebaseRepository {

    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    // Collection references
    private val teachersCollection = firestore.collection("teachers")
    private val studentsCollection = firestore.collection("students")
    private val classroomsCollection = firestore.collection("classrooms")
    private val subjectsCollection = firestore.collection("subjects")
    private val sessionsCollection = firestore.collection("sessions")
    private val attendanceCollection = firestore.collection("attendance")
    private val activeSessionsCollection = firestore.collection("active_sessions")

    // ==================== Authentication ====================

    suspend fun loginTeacher(email: String, password: String): Result<Teacher> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid ?: throw Exception("User ID not found")

            val snapshot = teachersCollection.document(userId).get().await()
            val teacher = snapshot.toObject(Teacher::class.java)
                ?: throw Exception("Teacher data not found")

            Result.success(teacher.copy(id = userId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginStudent(email: String, password: String): Result<Student> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid ?: throw Exception("User ID not found")

            val snapshot = studentsCollection.document(userId).get().await()
            val student = snapshot.toObject(Student::class.java)
                ?: throw Exception("Student data not found")

            Result.success(student.copy(id = userId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerTeacher(email: String, password: String, teacher: Teacher): Result<String> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid ?: throw Exception("Failed to create user")

            teachersCollection.document(userId).set(teacher.copy(id = userId)).await()

            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerStudent(email: String, password: String, student: Student): Result<String> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid ?: throw Exception("Failed to create user")

            studentsCollection.document(userId).set(student.copy(id = userId)).await()

            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Teacher Operations ====================

    suspend fun getTeacherClassrooms(teacherId: String): Result<List<Classroom>> {
        return try {
            val teacher = teachersCollection.document(teacherId).get().await()
                .toObject(Teacher::class.java) ?: throw Exception("Teacher not found")

            val classrooms = mutableListOf<Classroom>()
            for (classroomId in teacher.classrooms) {
                val snapshot = classroomsCollection.document(classroomId).get().await()
                snapshot.toObject(Classroom::class.java)?.let {
                    classrooms.add(it.copy(id = classroomId))
                }
            }

            Result.success(classrooms)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getClassroomSubjects(classroomId: String): Result<List<Subject>> {
        return try {
            val classroom = classroomsCollection.document(classroomId).get().await()
                .toObject(Classroom::class.java) ?: throw Exception("Classroom not found")

            val subjects = mutableListOf<Subject>()
            for (subjectId in classroom.subjects) {
                val snapshot = subjectsCollection.document(subjectId).get().await()
                snapshot.toObject(Subject::class.java)?.let {
                    subjects.add(it.copy(id = subjectId))
                }
            }

            Result.success(subjects)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createSession(
        teacherId: String,
        subjectId: String,
        classroomId: String,
        startTime: Long,
        endTime: Long,
        wifiSSID: String,
        wifiBSSID: String
    ): Result<String> {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

            val sessionDoc = sessionsCollection.document()
            val sessionId = sessionDoc.id

            val session = Session(
                id = sessionId,
                teacherId = teacherId,
                subjectId = subjectId,
                classroomId = classroomId,
                date = currentDate,
                startTime = startTime,
                endTime = endTime,
                status = SessionStatus.ACTIVE,
                wifiSSID = wifiSSID,
                wifiBSSID = wifiBSSID,
                attendanceWindow = true
            )

            sessionDoc.set(session).await()
            activeSessionsCollection.document(sessionId).set(session).await()

            Result.success(sessionId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closeSession(sessionId: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "status" to SessionStatus.CLOSED.name,
                "attendanceWindow" to false
            )

            sessionsCollection.document(sessionId).update(updates).await()
            activeSessionsCollection.document(sessionId).delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSessionAttendance(sessionId: String): Result<List<Pair<Student, Attendance>>> {
        return try {
            val session = sessionsCollection.document(sessionId).get().await()
                .toObject(Session::class.java) ?: throw Exception("Session not found")

            val classroom = classroomsCollection.document(session.classroomId).get().await()
                .toObject(Classroom::class.java) ?: throw Exception("Classroom not found")

            val attendanceList = mutableListOf<Pair<Student, Attendance>>()

            for (studentId in classroom.students) {
                val student = studentsCollection.document(studentId).get().await()
                    .toObject(Student::class.java)?.copy(id = studentId)

                // Query attendance by sessionId and studentId
                val attendanceQuery = attendanceCollection
                    .whereEqualTo("sessionId", sessionId)
                    .whereEqualTo("studentId", studentId)
                    .limit(1)
                    .get()
                    .await()

                val attendance = if (attendanceQuery.documents.isNotEmpty()) {
                    attendanceQuery.documents[0].toObject(Attendance::class.java)
                        ?: Attendance(
                            id = attendanceQuery.documents[0].id,
                            sessionId = sessionId,
                            studentId = studentId,
                            status = AttendanceStatus.ABSENT
                        )
                } else {
                    Attendance(
                        sessionId = sessionId,
                        studentId = studentId,
                        status = AttendanceStatus.ABSENT
                    )
                }

                if (student != null) {
                    attendanceList.add(Pair(student, attendance))
                }
            }

            Result.success(attendanceList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun overrideAttendance(
        sessionId: String,
        studentId: String,
        newStatus: AttendanceStatus,
        teacherId: String
    ): Result<Unit> {
        return try {
            // Find existing attendance document
            val query = attendanceCollection
                .whereEqualTo("sessionId", sessionId)
                .whereEqualTo("studentId", studentId)
                .limit(1)
                .get()
                .await()

            if (query.documents.isNotEmpty()) {
                val docId = query.documents[0].id
                val updates = mapOf(
                    "status" to newStatus.name,
                    "overriddenBy" to teacherId,
                    "markedBy" to MarkedBy.TEACHER.name,
                    "markedAt" to System.currentTimeMillis()
                )
                attendanceCollection.document(docId).update(updates).await()
            } else {
                // Create new attendance record
                val attendance = Attendance(
                    sessionId = sessionId,
                    studentId = studentId,
                    status = newStatus,
                    markedBy = MarkedBy.TEACHER,
                    overriddenBy = teacherId,
                    markedAt = System.currentTimeMillis()
                )
                attendanceCollection.add(attendance).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Student Operations ====================

    suspend fun getActiveSessionsForStudent(studentId: String): Result<List<ActiveSession>> {
        return try {
            val student = studentsCollection.document(studentId).get().await()
                .toObject(Student::class.java) ?: throw Exception("Student not found")

            val activeSessions = mutableListOf<ActiveSession>()

            // Get all active sessions
            val sessionsQuery = activeSessionsCollection
                .whereIn("classroomId", student.classrooms)
                .whereEqualTo("attendanceWindow", true)
                .get()
                .await()

            for (sessionDoc in sessionsQuery.documents) {
                val session = sessionDoc.toObject(Session::class.java) ?: continue

                val subject = subjectsCollection.document(session.subjectId).get().await()
                    .toObject(Subject::class.java)

                val teacher = teachersCollection.document(session.teacherId).get().await()
                    .toObject(Teacher::class.java)

                val classroom = classroomsCollection.document(session.classroomId).get().await()
                    .toObject(Classroom::class.java)

                activeSessions.add(
                    ActiveSession(
                        sessionId = session.id,
                        subjectName = subject?.name ?: "",
                        teacherName = teacher?.name ?: "",
                        classroomName = classroom?.name ?: "",
                        startTime = session.startTime,
                        endTime = session.endTime
                    )
                )
            }

            Result.success(activeSessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAttendance(
        sessionId: String,
        studentId: String,
        selfieBase64: String,
        verificationScore: Float
    ): Result<Unit> {
        return try {
            val attendance = Attendance(
                sessionId = sessionId,
                studentId = studentId,
                status = AttendanceStatus.PRESENT,
                markedAt = System.currentTimeMillis(),
                selfieBase64 = selfieBase64,
                verificationScore = verificationScore,
                markedBy = MarkedBy.STUDENT
            )

            attendanceCollection.add(attendance).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStudentSubjects(studentId: String): Result<List<SubjectAttendance>> {
        return try {
            val student = studentsCollection.document(studentId).get().await()
                .toObject(Student::class.java) ?: throw Exception("Student not found")

            val subjectAttendanceList = mutableListOf<SubjectAttendance>()

            for (classroomId in student.classrooms) {
                val classroom = classroomsCollection.document(classroomId).get().await()
                    .toObject(Classroom::class.java) ?: continue

                for (subjectId in classroom.subjects) {
                    val subject = subjectsCollection.document(subjectId).get().await()
                        .toObject(Subject::class.java) ?: continue

                    val (total, attended) = getAttendanceCountForSubject(studentId, subjectId)
                    val percentage = if (total > 0) (attended.toFloat() / total * 100) else 0f

                    subjectAttendanceList.add(
                        SubjectAttendance(
                            subjectId = subjectId,
                            subjectName = subject.name,
                            subjectCode = subject.code,
                            totalClasses = total,
                            attendedClasses = attended,
                            attendancePercentage = percentage
                        )
                    )
                }
            }

            Result.success(subjectAttendanceList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getAttendanceCountForSubject(studentId: String, subjectId: String): Pair<Int, Int> {
        var totalClasses = 0
        var attendedClasses = 0

        val sessionsQuery = sessionsCollection
            .whereEqualTo("subjectId", subjectId)
            .get()
            .await()

        for (sessionDoc in sessionsQuery.documents) {
            val session = sessionDoc.toObject(Session::class.java) ?: continue
            totalClasses++

            val attendanceQuery = attendanceCollection
                .whereEqualTo("sessionId", session.id)
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("status", AttendanceStatus.PRESENT.name)
                .limit(1)
                .get()
                .await()

            if (attendanceQuery.documents.isNotEmpty()) {
                attendedClasses++
            }
        }

        return Pair(totalClasses, attendedClasses)
    }

    // ==================== Get Session ====================

    suspend fun getSession(sessionId: String): Result<Session> {
        return try {
            val session = sessionsCollection.document(sessionId).get().await()
                .toObject(Session::class.java) ?: throw Exception("Session not found")

            Result.success(session.copy(id = sessionId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
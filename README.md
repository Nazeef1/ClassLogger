# ClassLogger - Face Recognition Based Attendance System

A comprehensive mobile attendance management system that uses AI-powered facial recognition to verify student identity when marking attendance, combined with WiFi-based location verification to ensure physical presence in the classroom.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [System Architecture](#system-architecture)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Installation and Setup](#installation-and-setup)
- [How It Works](#how-it-works)
- [API Endpoints](#api-endpoints)
- [Database Schema](#database-schema)
- [Security Considerations](#security-considerations)
- [Future Enhancements](#future-enhancements)
- [Contributors](#contributors)

---

## Overview

**ClassLogger** is a mobile-first attendance management system designed for educational institutions. The system eliminates proxy attendance by combining:

1. **Facial Recognition**: Students must take a selfie to mark attendance; the system verifies the face against pre-registered embeddings using deep learning (FaceNet CNN model).

2. **WiFi Verification**: Students must be connected to the classroom's specific WiFi network (verified by SSID and BSSID) to mark attendance, ensuring physical presence.

3. **Teacher Oversight**: Teachers can view real-time attendance, access attendance statistics, and override attendance status when necessary.

---

## Features

### For Students

| Feature | Description |
|---------|-------------|
| User Registration | Register with name matching college database records; face embeddings are pre-loaded |
| Login | Secure email/password authentication via Firebase Auth |
| View Active Sessions | See list of currently active attendance sessions for enrolled classrooms |
| Mark Attendance | Take a selfie; system verifies identity using facial recognition |
| View Attendance History | Check attendance records per subject with percentage calculations |
| WiFi Verification | Automatic verification of classroom WiFi connection |

### For Teachers

| Feature | Description |
|---------|-------------|
| User Registration/Login | Secure teacher authentication |
| Create Sessions | Start attendance sessions with time windows for enrolled subjects |
| View Active Sessions | Monitor currently running attendance sessions |
| Attendance Summary | View detailed present/absent statistics for each session |
| Override Attendance | Manually mark students present/absent when needed |
| View Selfies | Access student selfies taken during attendance marking |

### System Features

| Feature | Description |
|---------|-------------|
| Real-time Sync | Firebase Firestore for real-time data synchronization |
| Deep Learning Face Verification | FaceNet model for generating 512-dimensional face embeddings |
| KNN Classification | K-Nearest Neighbors with cosine similarity for identity matching |
| Confidence Threshold | 70% confidence required for successful face verification |
| MTCNN Face Detection | Multi-task Cascaded Convolutional Networks for robust face detection |

---

## System Architecture

```
+---------------------------------------------------------------------+
|                         CLASSLOGGER SYSTEM                          |
+---------------------------------------------------------------------+
|                                                                     |
|  +------------------+    +------------------+    +------------------+|
|  |   ANDROID APP    |    |  PYTHON BACKEND  |    |    FIREBASE      ||
|  |   (Kotlin)       |<-->|  (Flask/FastAPI) |    |   (Cloud DB)     ||
|  +--------+---------+    +--------+---------+    +--------+---------+|
|           |                       |                       |         |
|           |  HTTP/HTTPS           |                       |         |
|           |  (Cloudflare Tunnel)  |                       |         |
|           |                       |                       |         |
|  +--------v---------+    +--------v---------+    +--------v---------+|
|  |  - CameraX       |    |  - FaceNet CNN   |    |  - Auth          ||
|  |  - WiFi Utils    |    |  - MTCNN         |    |  - Firestore     ||
|  |  - Retrofit      |    |  - KNN Model     |    |  - Realtime DB   ||
|  |  - Coroutines    |    |  - OpenCV        |    |                  ||
|  +------------------+    +------------------+    +------------------+|
|                                                                     |
+---------------------------------------------------------------------+
```

### Attendance Flow

```
Student App                    Python Backend                 Firebase
    |                               |                            |
    |------- Login --------------->|                            |
    |                               |<---- Auth Token -----------|
    |                               |                            |
    |------- Get Active Sessions --|-------------------------->|
    |<----- Session List ----------|----------------------------|
    |                               |                            |
    | [Verify WiFi SSID + BSSID]    |                            |
    | [Capture Selfie via CameraX]  |                            |
    |                               |                            |
    |------- POST /predict ------->|                            |
    |        (selfie image)         |                            |
    |                               |                            |
    |                    [MTCNN Face Detection]                  |
    |                    [FaceNet Embedding]                     |
    |                    [KNN Prediction]                        |
    |                               |                            |
    |<----- {label, confidence} ---|                            |
    |                               |                            |
    | [If match and confidence >= 70%]                          |
    |------- Mark Attendance ------|-------------------------->|
    |<----- Success ---------------|----------------------------|
```

---

## Technology Stack

### Frontend (Android)

| Technology | Purpose |
|------------|---------|
| Kotlin | Primary programming language |
| Android SDK 34 | Target SDK (min SDK 24) |
| CameraX 1.3.1 | Modern camera API for selfie capture |
| Firebase Auth | User authentication |
| Firebase Firestore | Cloud database |
| Retrofit 2.9 | HTTP client for API calls |
| Coroutines | Asynchronous programming |
| Glide 4.16 | Image loading |
| MPAndroidChart | Attendance visualization charts |
| Material Design 1.11 | UI components |

### Backend (Python)

| Technology | Purpose |
|------------|---------|
| Flask 3.0 / FastAPI | Web framework for REST API |
| keras-facenet / facenet-pytorch | Pre-trained FaceNet model |
| MTCNN | Face detection in images |
| scikit-learn | KNN classifier with cosine distance |
| OpenCV | Image processing |
| NumPy | Numerical operations |
| firebase-admin | Server-side Firebase integration |

### Deep Learning Models

| Model | Architecture | Purpose |
|-------|--------------|---------|
| FaceNet | Inception-ResNet-V1 | Generates 512-D face embeddings |
| MTCNN | Cascaded CNN | Face detection and alignment |
| KNN | K-Nearest Neighbors (k=5) | Identity classification using cosine similarity |

---

## Project Structure

```
MAD_project/
|
+-- classlogger/                        # Android Studio Project
|   +-- ClassLogger/
|       +-- app/
|       |   +-- src/main/
|       |   |   +-- java/com/example/classlogger/
|       |   |   |   +-- models/         # Data models (Student, Teacher, Session, etc.)
|       |   |   |   +-- network/        # Retrofit API interface
|       |   |   |   +-- repository/     # Firebase and Face Recognition repositories
|       |   |   |   +-- ui/
|       |   |   |   |   +-- auth/       # Login and Registration activities
|       |   |   |   |   +-- student/    # Student dashboard, attendance, history
|       |   |   |   |   +-- teacher/    # Teacher dashboard, sessions, management
|       |   |   |   +-- utils/          # WiFi utilities
|       |   |   +-- res/                # Android resources (layouts, drawables)
|       |   |   +-- AndroidManifest.xml
|       |   +-- build.gradle.kts
|       +-- classlogger-server/         # Alternative Flask server
|           +-- facenet_server.py       # Flask-based face recognition server
|           +-- requirements.txt
|
+-- classlogger_backend/                # Primary FastAPI Backend
|   +-- main.py                         # FastAPI endpoints and KNN training
|   +-- face_service.py                 # Face processing functions
|   +-- data/
|   |   +-- face_embeddings.npz         # Pre-computed face embeddings
|   +-- requirements.txt
|
+-- dataset/                            # Training face images
|   +-- student_name_1/
|   |   +-- image1.jpg
|   |   +-- image2.jpg
|   +-- student_name_2/
|       +-- ...
|
+-- validate_dataset/                   # Validation images for testing
```

---

## Prerequisites

### Android Development
- Android Studio Hedgehog (2023.1.1) or later
- JDK 11 or higher
- Android device with API 24+ (or emulator)

### Backend Development
- Python 3.9 or higher
- pip (Python package manager)
- CUDA-compatible GPU (recommended for faster inference)

### Cloud Services
- Firebase project with:
  - Authentication enabled
  - Firestore database
  - Service account key (serviceAccountKey.json)

---

## Installation and Setup

### 1. Clone the Repository

```bash
git clone https://github.com/your-repo/classlogger.git
cd classlogger
```

### 2. Backend Setup

```bash
# Navigate to backend directory
cd classlogger_backend

# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Additional dependencies if using GPU
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu118
```

### 3. Prepare Face Dataset

1. Create folders for each student in the `dataset/` directory:
   ```
   dataset/
   +-- john_doe/
   |   +-- photo1.jpg
   |   +-- photo2.jpg
   |   +-- photo3.jpg
   +-- jane_smith/
       +-- ...
   ```

2. Use the exact name that students will register with (names are case-sensitive).

3. Run the embedding generation (automatically runs on server start):
   ```bash
   python main.py
   ```

### 4. Run the Backend Server

```bash
# For development
python main.py

# For production with Cloudflare Tunnel
cloudflared tunnel --url http://localhost:8000
```

Update the backend URL in `FaceNetApiClient.kt`:
```kotlin
private var BASE_URL = "https://your-tunnel-name.trycloudflare.com"
```

### 5. Firebase Setup

1. Create a Firebase project at https://console.firebase.google.com

2. Enable Authentication (Email/Password provider)

3. Create Firestore Database

4. Download `google-services.json` and place in `classlogger/ClassLogger/app/`

5. Download service account key and save as `serviceAccountKey.json` in backend directory

### 6. Android App Setup

1. Open `classlogger/ClassLogger` in Android Studio

2. Sync Gradle files

3. Build and run on device/emulator

---

## How It Works

### Student Registration Flow

1. Student opens app and clicks Register
2. Enters name (must match name in dataset folder)
3. Backend checks if name exists in face_embeddings.npz
4. If found, Firebase account is created
5. NPZ label updated from student name to Firebase user ID
6. Student can now login and mark attendance

Note: Face images must be pre-loaded in the dataset by administrators. This is because the college maintains a separate database of student photos, which are used to generate face embeddings before registration.

### Attendance Marking Flow

1. Student logs in and views active sessions
2. App verifies WiFi connection (SSID and BSSID must match classroom)
3. Student opens camera and captures selfie
4. Image sent to backend where MTCNN detects face
5. FaceNet extracts 512-dimensional embedding
6. KNN classifier predicts identity with confidence
7. If label matches student ID and confidence is at least 70%, attendance is marked as PRESENT in Firestore
8. Otherwise, error is shown and student can retry

### WiFi Verification

Each classroom is associated with a specific WiFi network:
- SSID: Network name (e.g., "ClassRoom_101_WiFi")
- BSSID: MAC address of the access point

Both must match for attendance to be allowed, preventing students from spoofing their location.

---

## API Endpoints

### Primary Backend (FastAPI - main.py)

| Endpoint | Method | Description |
|----------|--------|-------------|
| /health | GET | Health check |
| /predict | POST | Predict identity from face image (multipart form) |
| /add_person | POST | Add new person with images |
| /check-name | POST | Check if name exists in NPZ |
| /update-label | POST | Update label (name to Firebase ID) |

### Alternative Server (Flask - facenet_server.py)

| Endpoint | Method | Description |
|----------|--------|-------------|
| /health | GET | Server status check |
| /encode | POST | Encode face to get embedding (for registration) |
| /verify | POST | Verify face against stored embedding |
| /test | POST | Test image upload |

### Request/Response Examples

POST /predict
```bash
curl -X POST "http://localhost:8000/predict" \
  -F "file=@selfie.jpg"
```

Response:
```json
{
  "label": "firebase_user_id_123",
  "confidence": 0.85
}
```

---

## Database Schema

### Firestore Collections

#### students
```json
{
  "id": "firebase_uid",
  "name": "John Doe",
  "email": "john@example.com",
  "phone": "+1234567890",
  "rollNumber": "2021CS001",
  "classrooms": ["classroom_id_1", "classroom_id_2"],
  "faceEncoding": ""
}
```

#### teachers
```json
{
  "id": "firebase_uid",
  "name": "Prof. Smith",
  "email": "smith@example.com",
  "phone": "+1234567890",
  "classrooms": ["classroom_id_1"]
}
```

#### classrooms
```json
{
  "id": "classroom_id",
  "name": "Room 101",
  "wifiSSID": "ClassRoom_101_WiFi",
  "wifiBSSID": "aa:bb:cc:dd:ee:ff",
  "subjects": ["subject_id_1", "subject_id_2"],
  "students": ["student_id_1", "student_id_2"]
}
```

#### sessions
```json
{
  "id": "session_id",
  "teacherId": "teacher_firebase_uid",
  "subjectId": "subject_id",
  "classroomId": "classroom_id",
  "date": "2024-01-15",
  "startTime": 1705300800000,
  "endTime": 1705304400000,
  "status": "ACTIVE",
  "wifiSSID": "ClassRoom_101_WiFi",
  "wifiBSSID": "aa:bb:cc:dd:ee:ff",
  "attendanceWindow": true
}
```

#### attendance
```json
{
  "sessionId": "session_id",
  "studentId": "student_firebase_uid",
  "classroomId": "classroom_id",
  "subjectId": "subject_id",
  "subjectName": "Data Structures",
  "status": "PRESENT",
  "markedAt": 1705301000000,
  "markedBy": "STUDENT",
  "selfieUrl": "base64_encoded_image",
  "verificationScore": 85,
  "overriddenBy": ""
}
```

---

## Security Considerations

| Security Measure | Implementation |
|-----------------|----------------|
| Authentication | Firebase Auth with email/password |
| Location Verification | WiFi SSID and BSSID dual verification |
| Face Verification | 70% confidence threshold to prevent false positives |
| Teacher Override Audit | All overrides logged with teacher ID |
| Network Security | HTTPS via Cloudflare Tunnel |
| Selfie Storage | Base64 encoded in Firestore |

### Recommendations for Production

1. Use Firebase Storage for selfie images instead of base64 in Firestore
2. Implement rate limiting on the face recognition API
3. Add anti-spoofing (liveness detection) to prevent photo attacks
4. Use biometric local storage for session tokens
5. Implement proper RBAC (Role-Based Access Control)

---

## Future Enhancements

- Liveness Detection: Prevent photo spoofing attacks
- Bulk Attendance Export: Export to Excel/CSV
- Push Notifications: Alert students when sessions start
- Geofencing: Additional location verification
- Admin Dashboard: Web portal for institution management
- Analytics: Attendance trends and insights
- Multi-language Support: i18n for broader adoption
- Offline Mode: Queue attendance when offline

---

## Contributors

This project was developed as part of Mobile Application Development (MAD) coursework.

---

## License

This project is for educational purposes. Please contact the contributors for licensing information.

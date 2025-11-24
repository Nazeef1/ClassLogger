"""
FaceNet Face Recognition Server for ClassLogger
Complete setup with Firestore integration
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import numpy as np
import base64
import cv2
from PIL import Image
import io
import firebase_admin
from firebase_admin import credentials, firestore
from facenet_pytorch import MTCNN, InceptionResnetV1
import torch

app = Flask(__name__)
CORS(app)

# Initialize Firebase Admin SDK
# Download your serviceAccountKey.json from Firebase Console:
# Project Settings > Service Accounts > Generate New Private Key
cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)

# Initialize Firestore
db = firestore.client()

# Initialize FaceNet model
device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
print(f"Using device: {device}")

mtcnn = MTCNN(
    keep_all=False,
    device=device,
    min_face_size=40,
    thresholds=[0.6, 0.7, 0.7]
)

facenet = InceptionResnetV1(pretrained='vggface2').eval().to(device)

# Threshold for face matching
SIMILARITY_THRESHOLD = 0.7

def decode_base64_image(base64_string):
    """Decode base64 string to OpenCV image"""
    try:
        # Remove data URL prefix if present
        if ',' in base64_string:
            base64_string = base64_string.split(',')[1]
        
        # Remove whitespace
        base64_string = base64_string.strip()
        
        image_data = base64.b64decode(base64_string)
        image = Image.open(io.BytesIO(image_data))
        
        # Convert to RGB
        if image.mode != 'RGB':
            image = image.convert('RGB')
        
        return np.array(image)
    except Exception as e:
        print(f"Error decoding image: {e}")
        return None

def extract_face_embedding(image):
    """Extract face embedding using FaceNet"""
    try:
        # Convert numpy array to PIL Image for MTCNN
        if isinstance(image, np.ndarray):
            image = Image.fromarray(image)
        
        # Detect face and get aligned face
        face = mtcnn(image)
        
        if face is None:
            print("No face detected")
            return None
        
        # Get embedding
        face = face.unsqueeze(0).to(device)
        with torch.no_grad():
            embedding = facenet(face)
        
        return embedding.cpu().numpy()[0]
    except Exception as e:
        print(f"Error extracting embedding: {e}")
        return None

def calculate_similarity(embedding1, embedding2):
    """Calculate cosine similarity between two embeddings"""
    embedding1 = np.array(embedding1)
    embedding2 = np.array(embedding2)
    
    # Cosine similarity
    similarity = np.dot(embedding1, embedding2) / (
        np.linalg.norm(embedding1) * np.linalg.norm(embedding2)
    )
    return float(similarity)

def get_student_face_encoding(student_id):
    """Retrieve student's face encoding from Firestore"""
    try:
        # Get student document
        student_ref = db.collection('students').document(student_id)
        student_doc = student_ref.get()
        
        if not student_doc.exists:
            print(f"Student {student_id} not found")
            return None
        
        student_data = student_doc.to_dict()
        
        if 'faceEncoding' not in student_data:
            print(f"No face encoding for student {student_id}")
            return None
        
        # Decode the stored embedding
        encoding_str = student_data['faceEncoding']
        encoding = np.frombuffer(
            base64.b64decode(encoding_str), 
            dtype=np.float32
        )
        return encoding
    except Exception as e:
        print(f"Error retrieving student encoding: {e}")
        return None

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        'status': 'healthy',
        'device': str(device),
        'message': 'FaceNet server is running'
    })

@app.route('/encode', methods=['POST'])
def encode_face():
    """
    Encode a face image to get face embedding
    Used during student registration
    
    Request body:
    {
        "imageBase64": "base64_encoded_image"
    }
    
    Response:
    {
        "success": true,
        "encoding": "base64_encoded_embedding",
        "message": "Face encoded successfully"
    }
    """
    try:
        data = request.get_json()
        
        if 'imageBase64' not in data:
            return jsonify({
                'success': False,
                'message': 'No image provided'
            }), 400
        
        # Decode image
        image = decode_base64_image(data['imageBase64'])
        if image is None:
            return jsonify({
                'success': False,
                'message': 'Invalid image data'
            }), 400
        
        # Extract face embedding
        embedding = extract_face_embedding(image)
        if embedding is None:
            return jsonify({
                'success': False,
                'message': 'No face detected in image. Please ensure your face is clearly visible.'
            }), 400
        
        # Encode embedding to base64 for storage
        embedding_bytes = embedding.astype(np.float32).tobytes()
        embedding_base64 = base64.b64encode(embedding_bytes).decode('utf-8')
        
        return jsonify({
            'success': True,
            'encoding': embedding_base64,
            'message': 'Face encoded successfully'
        })
    
    except Exception as e:
        print(f"Error in encode_face: {e}")
        return jsonify({
            'success': False,
            'message': f'Server error: {str(e)}'
        }), 500

@app.route('/verify', methods=['POST'])
def verify_face():
    """
    Verify if captured face matches student's registered face
    Used during attendance marking
    
    Request body:
    {
        "studentId": "student_firebase_id",
        "imageBase64": "base64_encoded_image"
    }
    
    Response:
    {
        "isMatch": true/false,
        "confidence": 0.85,
        "message": "Verification successful"
    }
    """
    try:
        data = request.get_json()
        
        if 'studentId' not in data or 'imageBase64' not in data:
            return jsonify({
                'isMatch': False,
                'confidence': 0.0,
                'message': 'Missing required parameters (studentId, imageBase64)'
            }), 400
        
        student_id = data['studentId']
        
        # Get stored face encoding
        stored_embedding = get_student_face_encoding(student_id)
        if stored_embedding is None:
            return jsonify({
                'isMatch': False,
                'confidence': 0.0,
                'message': 'Student face encoding not found. Please register first.'
            }), 404
        
        # Decode captured image
        image = decode_base64_image(data['imageBase64'])
        if image is None:
            return jsonify({
                'isMatch': False,
                'confidence': 0.0,
                'message': 'Invalid image data'
            }), 400
        
        # Extract face embedding from captured image
        captured_embedding = extract_face_embedding(image)
        if captured_embedding is None:
            return jsonify({
                'isMatch': False,
                'confidence': 0.0,
                'message': 'No face detected in captured image. Please try again.'
            }), 400
        
        # Calculate similarity
        similarity = calculate_similarity(stored_embedding, captured_embedding)
        is_match = similarity >= SIMILARITY_THRESHOLD
        
        message = 'Face verified successfully!' if is_match else f'Face does not match (confidence: {similarity:.2f})'
        
        return jsonify({
            'isMatch': is_match,
            'confidence': similarity,
            'message': message
        })
    
    except Exception as e:
        print(f"Error in verify_face: {e}")
        return jsonify({
            'isMatch': False,
            'confidence': 0.0,
            'message': f'Server error: {str(e)}'
        }), 500

@app.route('/test', methods=['POST'])
def test_endpoint():
    """Test endpoint to check if image is being received correctly"""
    try:
        data = request.get_json()
        
        if 'imageBase64' not in data:
            return jsonify({
                'success': False,
                'message': 'No image provided'
            }), 400
        
        # Try to decode
        image = decode_base64_image(data['imageBase64'])
        if image is None:
            return jsonify({
                'success': False,
                'message': 'Failed to decode image'
            }), 400
        
        return jsonify({
            'success': True,
            'message': 'Image received successfully',
            'imageShape': image.shape
        })
    
    except Exception as e:
        return jsonify({
            'success': False,
            'message': str(e)
        }), 500

if __name__ == '__main__':
    print("=" * 50)
    print("FaceNet Server for ClassLogger")
    print("=" * 50)
    print(f"Device: {device}")
    print("Loading FaceNet models...")
    print("Server starting on http://localhost:5000")
    print("=" * 50)
    print("\nAvailable endpoints:")
    print("  GET  /health  - Check server status")
    print("  POST /encode  - Encode face for registration")
    print("  POST /verify  - Verify face for attendance")
    print("  POST /test    - Test image upload")
    print("=" * 50)
    
    app.run(host='0.0.0.0', port=5000, debug=True)
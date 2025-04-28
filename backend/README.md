# Face Recognition Attendance System Backend

This backend server provides a REST API for face recognition-based attendance marking. It integrates with Firebase for storage and database functionality and uses the face_recognition library for biometric verification.

## Features

- Face registration: Store reference face images for users
- Face verification: Compare a captured face against a stored reference
- Attendance marking: Record check-in and check-out times
- Attendance history: Retrieve attendance records for users

## Requirements

- Python 3.8+
- Firebase project with Firestore and Storage
- dlib and face_recognition libraries
- Flask server

## Setup

1. Install dependencies:
   ```
   pip install -r requirements.txt
   ```

2. Set up Firebase:
   - Create a Firebase project
   - Enable Firestore Database and Storage
   - Download service account credentials file
   - Place the credentials file in the project root or specify path in .env

3. Configure environment:
   - Copy `.env.example` to `.env`
   - Update the Firebase configuration in the .env file

4. Run the server:
   ```
   ./run.sh
   ```
   Or manually:
   ```
   python app.py
   ```

## API Endpoints

### Health Check
- `GET /health`
  - Response: `{"status": "ok", "message": "Face Recognition Backend is running"}`

### Face Registration
- `POST /api/register-face`
  - Request body: `{"sevarth_id": "USER123", "face_image": "base64_encoded_image"}`
  - Response: `{"message": "Face registered successfully", "face_image_url": "url_to_stored_image"}`

### Face Verification
- `POST /api/verify-face`
  - Request body: `{"sevarth_id": "USER123", "face_image": "base64_encoded_image"}`
  - Response: `{"verified": true, "confidence": 0.95, "message": "High confidence match"}`

### Mark Attendance
- `POST /api/mark-attendance`
  - Request body: `{"sevarth_id": "USER123", "type": "check_in", "verification_confidence": 0.95}`
  - Response: `{"message": "Attendance check_in marked successfully", "attendance_id": "record_id", "date": "2025-04-25", "time": "09:30:45"}`

### Attendance History
- `GET /api/attendance-history?sevarth_id=USER123&start_date=2025-04-01&end_date=2025-04-30`
  - Query params:
    - `sevarth_id`: Required - User's Sevarth ID
    - `start_date`: Optional - Start date in YYYY-MM-DD format
    - `end_date`: Optional - End date in YYYY-MM-DD format
  - Response: `{"attendance_records": [{...}, {...}], "count": 2}`

## Face Verification Logic

The backend uses the `face_recognition` library to:
1. Detect faces in both reference and captured images
2. Generate 128-dimensional face encodings for each face
3. Calculate the Euclidean distance between encodings
4. Apply confidence thresholds:
   - High confidence (≥55%): Immediate verification
   - Minimum confidence (≥45%): Verification with warning
   - Below minimum: Verification fails

## Integration with Android App

The Android app communicates with this backend using the following flow:
1. App captures a face image when user clicks "Check In" or "Check Out"
2. App sends the image to the backend for verification against stored reference
3. If verified, backend marks attendance in Firebase Firestore
4. App displays success or error message based on verification result

## Test Credentials

- User:
  - Sevarth ID: user_sevarth
  - Password: user123
  - Role: user

- Admin:
  - Sevarth ID: admin_sevarth
  - Password: admin123
  - Role: admin

## Security Notes

In production:
- Change the SECRET_KEY
- Use environment variables for sensitive data
- Implement a proper database
- Add proper error handling and logging
- Enable HTTPS 
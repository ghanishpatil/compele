# FaceAttend - Face Recognition Attendance System

A modern face recognition-based attendance system with an Android app frontend and AWS-hosted backend. This system allows users to mark attendance using facial verification and tracks attendance records in real-time.

## Features

- **Face Recognition Authentication**: Secure attendance marking using facial recognition
- **Real-time Attendance Tracking**: Check-in and check-out with timestamp verification
- **Location Awareness**: Validates user presence within designated office locations
- **Attendance History**: View and track past attendance records
- **Admin Management**: Add users and office locations through an admin interface
- **Cloud Backend**: Scalable AWS-hosted backend with Docker containerization

## Technology Stack

### Mobile App (Android)
- Java/Kotlin
- Android Jetpack components
- Firebase Authentication
- Google ML Kit for face detection
- Retrofit for API communication

### Backend
- Python Flask API
- Face recognition processing
- Docker containerization
- AWS ECS/ECR deployment
- Load balancing and auto-scaling

## Setup Instructions

### Prerequisites
- Android Studio 4.2+
- Python 3.8+
- Docker and Docker Compose
- AWS CLI configured (for backend deployment)

### Android App Setup
1. Clone the repository
2. Open the project in Android Studio
3. Update the `RetrofitClient.java` base URL to your backend API endpoint
4. Build and run the application

### Backend Setup
1. Navigate to the `backend` directory
2. Set up a Python virtual environment: `python -m venv venv`
3. Activate the environment: `source venv/bin/activate` (Linux/Mac) or `venv\Scripts\activate` (Windows)
4. Install dependencies: `pip install -r requirements.txt`
5. Run locally: `python app.py`

### AWS Deployment
The backend can be deployed to AWS using the provided scripts:
1. Ensure AWS CLI is configured with appropriate permissions
2. Run the deployment script: `./aws-deploy.sh` (Linux/Mac) or `aws-deploy.ps1` (Windows)
3. Update the Android app's API endpoint to the generated load balancer URL

## License
[MIT License](LICENSE)

## Acknowledgments
- The ML model is based on the face_recognition library
- Thanks to all contributors who have helped improve this system 
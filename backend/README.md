# Face Recognition Attendance Backend

A Flask-based backend service for face recognition attendance system that uses Firebase for authentication and data storage.

## Features

- Face registration and verification
- User authentication with Firebase
- Attendance tracking
- Admin management portal
- Location-based attendance

## Local Development

### Prerequisites

- Docker and Docker Compose
- Firebase project with Authentication, Firestore, and Storage enabled
- Firebase Admin SDK credentials file

### Setup

1. Clone the repository
2. Navigate to the backend directory
3. Place your Firebase Admin SDK credentials file in the backend directory
4. Update the `docker-compose.yml` file with the correct path to your credentials file
5. Run the application with Docker Compose:

```bash
docker-compose up --build
```

The server will be available at http://localhost:5000

## Deployment to Render

1. Push your code to GitHub
2. Sign up for a Render account at https://render.com
3. Create a new Web Service and select your repository
4. Configure the service:
   - Environment: Docker
   - Build directory: backend
   - Instance Type: Choose appropriate plan (start with Free tier)
   - Region: Choose closest to your users
   
5. Environment Variables
   
   Add the following environment variables:
   - `FIREBASE_CREDENTIALS_JSON`: Copy and paste the entire content of your Firebase Admin SDK credentials JSON file into this variable

6. Deploy the service

Render will automatically build and deploy your Docker container. You can find the URL to your service in the Render dashboard.

## API Endpoints

- `/register/admin` - Register a new admin
- `/register/user` - Register a new user
- `/login` - User/Admin login
- `/api/register-face` - Register a user's face
- `/api/verify-face` - Verify a user's face
- `/api/mark-attendance` - Mark attendance
- `/api/attendance-history` - Get attendance history

## Environment Variables

- `FIREBASE_CREDENTIALS_PATH`: Path to the Firebase Admin SDK credentials file
- `FIREBASE_CREDENTIALS_JSON`: Alternative method to provide Firebase credentials as a JSON string

## Security Notes

- Never commit your Firebase credentials file to version control
- Always use environment variables for sensitive information
- Use HTTPS for all API communications in production 
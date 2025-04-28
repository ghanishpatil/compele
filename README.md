# Sub-Divisional Office Login System

This project consists of an Android app frontend and a Python Flask backend for the Sub-Divisional Office login system.

## Backend Setup

1. Navigate to the backend directory:
```bash
cd backend
```

2. Create a virtual environment:
```bash
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
```

3. Install dependencies:
```bash
pip install -r requirements.txt
```

4. Run the Flask server:
```bash
python app.py
```

The backend server will start on http://localhost:5000

## Android App Setup

1. Open the project in Android Studio

2. Sync the project with Gradle files

3. Update the backend URL in `LoginActivity.java` if needed (default is set to 10.0.2.2:5000 for Android Emulator)

4. Build and run the app

## Test Credentials

- User:
  - Sevarth ID: user_sevarth
  - Password: user123
  - Role: user

- Admin:
  - Sevarth ID: admin_sevarth
  - Password: admin123
  - Role: admin

## Features

- Role-based login (User/Admin)
- Secure password handling
- JWT token-based authentication
- Modern Material Design UI
- Error handling and validation
- Network state handling

## Security Notes

For production deployment:
- Use HTTPS
- Implement proper session management
- Store sensitive data securely
- Use environment variables for configuration
- Implement rate limiting
- Add proper logging and monitoring 
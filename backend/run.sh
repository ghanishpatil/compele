#!/bin/bash

# Set the Python path to include the current directory
export PYTHONPATH=$PYTHONPATH:.

# Set environment variables from .env file if it exists
if [ -f .env ]; then
    echo "Loading environment variables from .env"
    export $(grep -v '^#' .env | xargs)
fi

# Check if Firebase credentials file exists
if [ ! -f "$FIREBASE_CREDENTIALS_PATH" ]; then
    echo "ERROR: Firebase credentials file not found at $FIREBASE_CREDENTIALS_PATH"
    echo "Please set the FIREBASE_CREDENTIALS_PATH environment variable in .env file"
    exit 1
fi

# Start the Flask server with Gunicorn
echo "Starting Face Recognition Attendance Backend server..."
gunicorn app:app --bind 0.0.0.0:5000 --workers 2 --timeout 120 
#!/bin/bash

# Script to deploy the backend to Render

echo "========== Face Recognition Attendance Backend Deployment =========="
echo "This script will help you deploy your backend to Render."
echo ""

# Check if curl is installed
if ! command -v curl &> /dev/null; then
    echo "Error: curl is not installed. Please install it before proceeding."
    exit 1
fi

# Check if the Firebase credentials file exists
FIREBASE_CREDS_FILE="startup-cf3fd-firebase-adminsdk-fbsvc-893409a921.json"
if [ ! -f "$FIREBASE_CREDS_FILE" ]; then
    echo "Warning: Firebase credentials file not found at $FIREBASE_CREDS_FILE"
    read -p "Enter the path to your Firebase credentials file: " FIREBASE_CREDS_FILE
    
    if [ ! -f "$FIREBASE_CREDS_FILE" ]; then
        echo "Error: File not found. Exiting."
        exit 1
    fi
fi

echo "Found Firebase credentials file: $FIREBASE_CREDS_FILE"

# Read the Firebase credentials file
FIREBASE_CREDENTIALS_JSON=$(cat "$FIREBASE_CREDS_FILE")

echo ""
echo "Instructions for Render deployment:"
echo "1. Log in to your Render account at https://render.com"
echo "2. Create a new Web Service"
echo "3. Connect your GitHub repository"
echo "4. Configure your service with the following settings:"
echo "   - Environment: Docker"
echo "   - Root Directory: backend"
echo "   - Branch: main (or your preferred branch)"
echo "   - Instance Type: Free (or your preferred plan)"
echo ""
echo "5. Add the following environment variable:"
echo "   - Key: FIREBASE_CREDENTIALS_JSON"
echo "   - Value: Copy the JSON content below (between the lines)"
echo ""
echo "-------------------- FIREBASE CREDENTIALS JSON --------------------"
echo "$FIREBASE_CREDENTIALS_JSON"
echo "-------------------------------------------------------------------"
echo ""
echo "Note: If you're using the Render CLI, you can automate this deployment."
echo "See https://render.com/docs/cli for more information."
echo ""
echo "Deployment preparation complete!" 
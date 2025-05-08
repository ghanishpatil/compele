FROM python:3.8-slim

# Install system dependencies required for dlib and face_recognition
RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libglib2.0-0 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Set the working directory
WORKDIR /app

# Copy requirements first to leverage Docker cache
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy the rest of the application
COPY . .

# Make the run script executable
RUN chmod +x run.sh

# Create a setup script to handle environment variables
RUN echo '#!/bin/bash\n\
# Create Firebase credentials file from environment variable if provided\n\
if [ -n "$FIREBASE_CREDENTIALS_JSON" ]; then\n\
    echo "Creating Firebase credentials file from environment variable"\n\
    echo $FIREBASE_CREDENTIALS_JSON > /app/firebase-credentials.json\n\
    export FIREBASE_CREDENTIALS_PATH="/app/firebase-credentials.json"\n\
fi\n\
\n\
# Run the main app\n\
exec ./run.sh\n\
' > /app/entrypoint.sh

# Make the entrypoint script executable
RUN chmod +x /app/entrypoint.sh

# Expose the port the app runs on
EXPOSE 5000

# Use the entrypoint script as the entry point
CMD ["/app/entrypoint.sh"] 
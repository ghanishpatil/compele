# AWS Deployment Guide for Face Recognition Backend

This guide explains how to deploy the Face Recognition Backend to AWS using Docker and AWS Elastic Container Service (ECS).

## Prerequisites

1. **AWS Account**: You need an active AWS account.
2. **AWS CLI**: Installed and configured with appropriate permissions.
3. **Docker**: Installed on your local machine.
4. **Firebase Credentials**: Your Firebase service account credentials file.

## Setup Local Environment

1. **Create a .env file**:
   ```
   # Flask configuration
   FLASK_APP=app.py
   FLASK_ENV=production
   SECRET_KEY=your-secret-key-change-this-in-production

   # Firebase configuration
   FIREBASE_CREDENTIALS_PATH=startup-cf3fd-firebase-adminsdk-fbsvc-893409a921.json
   FIREBASE_STORAGE_BUCKET=startup-cf3fd.firebasestorage.app

   # Face Recognition settings
   MINIMUM_CONFIDENCE_THRESHOLD=0.45
   HIGH_CONFIDENCE_THRESHOLD=0.55
   ```

2. **Test locally with Docker**:
   ```bash
   # Build and run using Docker Compose
   docker-compose up --build
   ```

## AWS Deployment Methods

### Method 1: Manual Deployment with AWS CLI

1. **Configure the AWS CLI**:
   ```bash
   aws configure
   ```

2. **Set Environment Variables**:
   ```bash
   export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
   ```

3. **Make the deployment script executable**:
   ```bash
   chmod +x aws-deploy.sh
   ```

4. **Run the deployment script**:
   ```bash
   ./aws-deploy.sh
   ```

### Method 2: CloudFormation Deployment

1. **Create a new CloudFormation Stack**:
   ```bash
   aws cloudformation create-stack \
     --stack-name face-recognition-backend \
     --template-body file://aws-cloudformation.yml \
     --parameters \
       ParameterKey=VpcId,ParameterValue=vpc-xxxxxxxx \
       ParameterKey=SubnetIds,ParameterValue=subnet-xxxxxxxx,subnet-yyyyyyyy \
       ParameterKey=DockerImageUrl,ParameterValue=$AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/face-recognition-backend:latest \
     --capabilities CAPABILITY_IAM
   ```

2. **Monitor Stack Creation**:
   ```bash
   aws cloudformation describe-stacks --stack-name face-recognition-backend
   ```

3. **Get the Load Balancer URL**:
   ```bash
   aws cloudformation describe-stacks \
     --stack-name face-recognition-backend \
     --query "Stacks[0].Outputs[?OutputKey=='LoadBalancerDNS'].OutputValue" \
     --output text
   ```

## Environment Variables in AWS

You need to set up environment variables in your ECS task definition. This can be done through:

1. **AWS Console**: Update the task definition with environment variables.
2. **CloudFormation**: Update the `Environment` section in the task definition.
3. **AWS Secrets Manager**: For sensitive information like Firebase credentials.

## Handling Firebase Credentials

For Firebase credentials, you have two options:

1. **Build the Credentials into the Docker Image**: Not recommended for security.
2. **Use AWS Secrets Manager**:
   ```bash
   # Store credentials in Secrets Manager
   aws secretsmanager create-secret \
     --name face-recognition/firebase-credentials \
     --secret-string file://startup-cf3fd-firebase-adminsdk-fbsvc-893409a921.json
   ```

   Then update your task definition to use the secret.

## Scaling the Application

The CloudFormation template sets up an Auto Scaling Group. You can modify it to adjust scaling:

1. **Set desired, minimum, and maximum capacity**.
2. **Configure scaling policies based on CPU/memory usage**.

## Health Checks and Monitoring

1. **Health Check Endpoint**: The app has a `/health` endpoint for the load balancer.
2. **CloudWatch Metrics**: Monitor your ECS services and containers.
3. **CloudWatch Alarms**: Set up alarms for high CPU or memory usage.

## Troubleshooting

1. **Check Container Logs**:
   ```bash
   aws logs get-log-events \
     --log-group-name /ecs/face-recognition-backend \
     --log-stream-name ecs/face-recognition-container/[TASK-ID]
   ```

2. **Check ECS Service Events**:
   ```bash
   aws ecs describe-services \
     --cluster face-recognition-cluster \
     --services face-recognition-service
   ```

3. **Common Issues**:
   - Missing environment variables
   - Incorrect Firebase credentials path
   - Insufficient container resources (memory/CPU)
   - Network connectivity issues 
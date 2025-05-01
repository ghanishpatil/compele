# AWS Deployment Script for Backend Docker Container (PowerShell version)

# Get AWS Account ID
$AWS_ACCOUNT_ID = aws sts get-caller-identity --query Account --output text
if (-not $AWS_ACCOUNT_ID) {
    Write-Error "Failed to get AWS Account ID. Make sure you're logged in with 'aws configure'."
    exit 1
}
Write-Host "Using AWS Account ID: $AWS_ACCOUNT_ID"

# Configuration - Replace these with your actual values
$AWS_REGION = "eu-north-1"
$ECR_REPOSITORY_NAME = "face-recognition-backend"
$ECS_CLUSTER_NAME = "face-recognition-cluster"
$ECS_SERVICE_NAME = "face-recognition-service"
$ECS_TASK_FAMILY = "face-recognition-task"
$ECS_CONTAINER_NAME = "face-recognition-container"

# Step 1: Authenticate Docker to Amazon ECR
Write-Host "Logging in to Amazon ECR..."
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"

# Step 2: Create ECR repository if it doesn't exist
Write-Host "Creating ECR repository if it doesn't exist..."
try {
    aws ecr describe-repositories --repository-names $ECR_REPOSITORY_NAME --region $AWS_REGION
    Write-Host "Repository already exists."
} catch {
    Write-Host "Creating repository..."
    aws ecr create-repository --repository-name $ECR_REPOSITORY_NAME --region $AWS_REGION
}

# Step 3: Build the Docker image
Write-Host "Building Docker image..."
docker build -t $ECR_REPOSITORY_NAME .

# Step 4: Tag the Docker image
Write-Host "Tagging Docker image..."
docker tag "$($ECR_REPOSITORY_NAME):latest" "$($AWS_ACCOUNT_ID).dkr.ecr.$($AWS_REGION).amazonaws.com/$($ECR_REPOSITORY_NAME):latest"

# Step 5: Push the Docker image to ECR
Write-Host "Pushing Docker image to ECR..."
docker push "$($AWS_ACCOUNT_ID).dkr.ecr.$($AWS_REGION).amazonaws.com/$($ECR_REPOSITORY_NAME):latest"

# Step 6: Update the ECS service to use the new image
Write-Host "Updating ECS service..."
aws ecs update-service --cluster $ECS_CLUSTER_NAME --service $ECS_SERVICE_NAME --force-new-deployment --region $AWS_REGION

Write-Host "Deployment completed!"
Write-Host "Your container should be deploying to ECS. Check the AWS Console for status." 
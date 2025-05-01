# Script to deploy the CloudFormation stack for face recognition backend

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
$STACK_NAME = "face-recognition-backend"

# Prompt for VPC and Subnet IDs
$VPC_ID = Read-Host -Prompt "Enter your VPC ID (e.g., vpc-12345678)"
if (-not $VPC_ID) {
    Write-Error "VPC ID is required"
    exit 1
}

$SUBNET_IDS = Read-Host -Prompt "Enter your Subnet IDs (comma-separated, e.g., subnet-123,subnet-456)"
if (-not $SUBNET_IDS) {
    Write-Error "Subnet IDs are required"
    exit 1
}

# Validate that Docker image exists in ECR
$DOCKER_IMAGE_URL = "$($AWS_ACCOUNT_ID).dkr.ecr.$($AWS_REGION).amazonaws.com/$($ECR_REPOSITORY_NAME):latest"
try {
    $imageExists = aws ecr describe-images --repository-name $ECR_REPOSITORY_NAME --image-ids imageTag=latest --region $AWS_REGION
    Write-Host "Docker image exists in ECR: $DOCKER_IMAGE_URL"
} catch {
    Write-Error "Docker image not found in ECR. Please build and push the image first using the aws-deploy.ps1 script."
    exit 1
}

# Deploy CloudFormation stack
Write-Host "Deploying CloudFormation stack..."
Write-Host "Stack Name: $STACK_NAME"
Write-Host "VPC ID: $VPC_ID"
Write-Host "Subnet IDs: $SUBNET_IDS"
Write-Host "Docker Image URL: $DOCKER_IMAGE_URL"

# Check if stack already exists
$stackExists = $false
try {
    $stack = aws cloudformation describe-stacks --stack-name $STACK_NAME --region $AWS_REGION
    $stackExists = $true
    Write-Host "Stack already exists, will update it"
} catch {
    Write-Host "Stack does not exist, will create it"
}

# Create or update the stack
if ($stackExists) {
    # Update the stack
    aws cloudformation update-stack `
        --stack-name $STACK_NAME `
        --template-body file://aws-cloudformation.yml `
        --parameters `
            ParameterKey=VpcId,ParameterValue=$VPC_ID `
            ParameterKey=SubnetIds,ParameterValue=\"$SUBNET_IDS\" `
            ParameterKey=DockerImageUrl,ParameterValue=$DOCKER_IMAGE_URL `
        --capabilities CAPABILITY_IAM `
        --region $AWS_REGION
} else {
    # Create the stack
    aws cloudformation create-stack `
        --stack-name $STACK_NAME `
        --template-body file://aws-cloudformation.yml `
        --parameters `
            ParameterKey=VpcId,ParameterValue=$VPC_ID `
            ParameterKey=SubnetIds,ParameterValue=\"$SUBNET_IDS\" `
            ParameterKey=DockerImageUrl,ParameterValue=$DOCKER_IMAGE_URL `
        --capabilities CAPABILITY_IAM `
        --region $AWS_REGION
}

# Wait for the stack to be created/updated
Write-Host "Waiting for stack operation to complete..."
if ($stackExists) {
    aws cloudformation wait stack-update-complete --stack-name $STACK_NAME --region $AWS_REGION
} else {
    aws cloudformation wait stack-create-complete --stack-name $STACK_NAME --region $AWS_REGION
}

# Get the Load Balancer URL
$LB_URL = aws cloudformation describe-stacks --stack-name $STACK_NAME --query "Stacks[0].Outputs[?OutputKey=='LoadBalancerDNS'].OutputValue" --output text --region $AWS_REGION

if ($LB_URL) {
    Write-Host "Deployment successful!"
    Write-Host "Your backend is now accessible at: http://$LB_URL"
    
    # Offer to update app configuration
    $updateApp = Read-Host -Prompt "Do you want to update your app configuration to use this backend? (y/N)"
    if ($updateApp -eq "y" -or $updateApp -eq "Y") {
        & .\update-app-config.ps1 -BackendUrl "http://$LB_URL"
    }
} else {
    Write-Host "Deployment completed, but couldn't retrieve Load Balancer URL."
    Write-Host "Check the AWS CloudFormation Console for details."
}

Write-Host "Deployment process completed!" 
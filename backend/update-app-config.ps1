# Script to update the frontend app configuration to point to AWS backend

param(
    [Parameter(Mandatory=$true)]
    [string]$BackendUrl
)

Write-Host "Updating app configuration to use backend at: $BackendUrl"

# Find the app configuration file
$appDir = Join-Path -Path ".." -ChildPath "app"
$configFiles = @(
    "src/config.js",
    "src/config/index.js",
    "src/utils/config.js",
    "src/constants/api.js",
    "src/api/config.js"
)

$configFileFound = $false

foreach ($configPath in $configFiles) {
    $fullPath = Join-Path -Path $appDir -ChildPath $configPath
    if (Test-Path $fullPath) {
        Write-Host "Found configuration file at: $fullPath"
        $configFileFound = $true
        
        # Read the file content
        $content = Get-Content -Path $fullPath -Raw
        
        # Replace API URL with the new backend URL
        # This is a simplified approach - you might need to adjust the regex based on your actual config file
        $updatedContent = $content -replace '(["'']https?://[^"'']*?["'']|baseUrl\s*=\s*["'']https?://[^"'']*?["'']|API_URL\s*=\s*["'']https?://[^"'']*?["''])', "`"$BackendUrl`""
        
        # Write the updated content back to the file
        Set-Content -Path $fullPath -Value $updatedContent
        
        Write-Host "Updated configuration to use backend: $BackendUrl"
    }
}

if (-not $configFileFound) {
    Write-Host "Warning: Could not find a configuration file to update!"
    Write-Host "You will need to manually update your application to point to: $BackendUrl"
}

Write-Host "Configuration update complete."
Write-Host "Remember to rebuild your frontend application with the updated configuration!" 
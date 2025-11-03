# PowerShell script to get SHA fingerprints for Firebase
Write-Host "Getting SHA fingerprints for Firebase configuration..." -ForegroundColor Green
Write-Host ""

# Default debug keystore location
$debugKeystore = "$env:USERPROFILE\.android\debug.keystore"

if (Test-Path $debugKeystore) {
    Write-Host "Found debug keystore at: $debugKeystore" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "To get SHA fingerprints, run this command:" -ForegroundColor Cyan
    Write-Host 'keytool -list -v -keystore "' $debugKeystore '" -alias androiddebugkey -storepass android -keypass android | Select-String -Pattern "SHA1|SHA256"' -ForegroundColor White
    Write-Host ""
    Write-Host "Or use Android Studio:" -ForegroundColor Cyan
    Write-Host "1. Open your project in Android Studio" -ForegroundColor White
    Write-Host "2. Click Gradle tab (usually on the right side)" -ForegroundColor White
    Write-Host "3. Navigate to: app > Tasks > android > signingReport" -ForegroundColor White
    Write-Host "4. Double-click signingReport to run it" -ForegroundColor White
    Write-Host "5. Copy the SHA-1 and SHA-256 values from the output" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host "Debug keystore not found at: $debugKeystore" -ForegroundColor Red
    Write-Host "If you're using a custom keystore, provide its path." -ForegroundColor Yellow
}

Write-Host "========================================" -ForegroundColor Green
Write-Host "Steps to fix Firebase Auth CONFIGURATION_NOT_FOUND:" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "1. Go to Firebase Console: https://console.firebase.google.com/" -ForegroundColor Cyan
Write-Host "2. Select your project: eventease-a0e05" -ForegroundColor Cyan
Write-Host "3. Go to Project Settings (gear icon)" -ForegroundColor Cyan
Write-Host "4. Scroll down to 'Your apps' section" -ForegroundColor Cyan
Write-Host "5. Click on your Android app (package: com.example.EventEase)" -ForegroundColor Cyan
Write-Host "6. Click 'Add fingerprint' button" -ForegroundColor Cyan
Write-Host "7. Add both SHA-1 and SHA-256 fingerprints" -ForegroundColor Cyan
Write-Host "8. Go to Authentication > Sign-in method" -ForegroundColor Cyan
Write-Host "9. Enable 'Email/Password' authentication" -ForegroundColor Cyan
Write-Host "10. Download the updated google-services.json" -ForegroundColor Cyan
Write-Host "11. Replace app/google-services.json with the new file" -ForegroundColor Cyan
Write-Host "12. Rebuild and run the app" -ForegroundColor Cyan
Write-Host ""


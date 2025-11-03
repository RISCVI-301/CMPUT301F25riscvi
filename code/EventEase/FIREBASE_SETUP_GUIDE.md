# Firebase Authentication Setup Guide

## Problem
The app shows "CONFIGURATION_NOT_FOUND" error when trying to sign up. This happens because Firebase Auth requires OAuth client configuration, which is created when SHA fingerprints are added to your Firebase project.

## Solution - Complete Setup Steps

### Step 1: Get SHA Fingerprints

#### Option A: Using Android Studio (Recommended)
1. Open your project in Android Studio
2. Open the **Gradle** tab (usually on the right side)
3. Navigate to: `EventEase` → `app` → `Tasks` → `android` → `signingReport`
4. Double-click `signingReport` to run it
5. In the output at the bottom, look for:
   ```
   Variant: debug
   SHA1: XX:XX:XX:XX... (copy this)
   SHA256: XX:XX:XX:XX... (copy this)
   ```

#### Option B: Using Command Line
1. Open PowerShell or Command Prompt
2. Navigate to your project directory
3. Run:
   ```powershell
   cd "E:\University\CMPUT 301\CMPUT301F25riscvi\code\EventEase"
   .\gradlew signingReport
   ```
4. Look for SHA1 and SHA256 values in the output

### Step 2: Configure Firebase Console

1. **Go to Firebase Console**
   - Open https://console.firebase.google.com/
   - Sign in with your Google account

2. **Select Your Project**
   - Click on project: **eventease-a0e05**

3. **Add SHA Fingerprints**
   - Click the **gear icon** ⚙️ next to "Project overview"
   - Select **Project settings**
   - Scroll down to **"Your apps"** section
   - Find your Android app: **com.example.EventEase**
   - Click **"Add fingerprint"** button
   - Paste your **SHA-1** fingerprint: `C6:EF:3E:54:51:AC:E1:86:85:3D:0A:3F:8C:6F:74:CA:A6:1A:BB:02`
   - Click **Save**
   - Click **"Add fingerprint"** again
   - Paste your **SHA-256** fingerprint: `8F:58:47:74:84:75:34:07:85:D4:6B:22:D7:F6:66:51:3A:4D:46:98:5D:EC:79:F1:68:F7:8B:7B:CD:CB:88:12`
   - Click **Save**

4. **Enable Email/Password Authentication**
   - In the left sidebar, click **Authentication**
   - Click the **Sign-in method** tab
   - Find **"Email/Password"** in the list
   - Click on it
   - Toggle **"Enable"** to ON
   - Click **Save**

5. **Download Updated google-services.json**
   - Go back to **Project settings** (gear icon)
   - Under **"Your apps"**, find your Android app
   - Click **"Download google-services.json"** button
   - Replace the file at `app/google-services.json` with the new one

### Step 3: Verify Configuration

1. Open the new `google-services.json` file
2. Check that the `oauth_client` array is no longer empty
3. It should contain entries like:
   ```json
   "oauth_client": [
     {
       "client_id": "...",
       "client_type": 3
     }
   ]
   ```

### Step 4: Rebuild the App

1. In Android Studio, click **File** → **Invalidate Caches / Restart**
2. Choose **Invalidate and Restart**
3. After restart, build the app again:
   - **Build** → **Rebuild Project**

### Step 5: Test Sign Up

1. Run the app
2. Try to sign up with a new account
3. It should now work without the CONFIGURATION_NOT_FOUND error

## Important Notes

- **Test Mode**: The database being in test mode doesn't affect authentication. However, make sure your Firestore security rules allow authenticated users to read/write if needed.

- **OAuth Clients**: These are automatically created by Firebase when you add SHA fingerprints. You don't need to create them manually.

- **If Still Not Working**: 
  - Wait a few minutes after adding SHA fingerprints (Firebase needs time to propagate)
  - Make sure you downloaded the NEW google-services.json AFTER adding fingerprints
  - Verify the package name matches: `com.example.EventEase`

## Troubleshooting

If you still see errors:
1. Double-check that SHA fingerprints were added correctly (no spaces, correct format)
2. Verify Email/Password is enabled in Authentication settings
3. Make sure the downloaded google-services.json has oauth_client entries
4. Try clearing app data and uninstalling/reinstalling the app


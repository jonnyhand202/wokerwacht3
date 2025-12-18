# Firebase Setup Guide for WorkWatch

This document explains how to set up Firebase for the WorkWatch application.

## Prerequisites

- A Google account
- Firebase project created in [Firebase Console](https://console.firebase.google.com/)
- Android app registered in the Firebase project

## Step 1: Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Create a new project" or select an existing one
3. Give it a name (e.g., "WorkWatch")
4. Click "Continue"
5. Configure Google Analytics (optional)
6. Click "Create project" and wait for initialization

## Step 2: Register Android App

1. In Firebase Console, click the "Android" icon to add an Android app
2. Enter the package name: `com.workwatch`
3. (Optional) Enter the app nickname: `WorkWatch`
4. (Optional) Enter the Debug Signing Certificate SHA-1:
   - To get SHA-1:
   ```bash
   # On Windows:
   keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android

   # On macOS/Linux:
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
5. Click "Register app"

## Step 3: Download google-services.json

1. After registering, download the `google-services.json` file
2. Place the file in: `app/src/google-services.json`
3. The file location is critical - Gradle won't find it anywhere else

### File Structure

```
wokerwacht3/
├── app/
│   ├── src/
│   │   ├── google-services.json    <-- Place the file here
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/
│   │   │   └── res/
│   │   └── ...
│   └── build.gradle.kts
├── build.gradle.kts
└── ...
```

## Step 4: Enable Firestore Database

1. In Firebase Console, go to "Firestore Database"
2. Click "Create Database"
3. Select: **Start in production mode** (you'll set rules later)
4. Select a location (closest to your users)
5. Click "Create"

## Step 5: Set Up Firestore Security Rules

1. In Firestore, go to "Rules" tab
2. Replace the default rules with:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Allow authenticated users to read/write their own data
    match /hash_leaks/{document=**} {
      allow read, write: if request.auth != null;
    }
    match /worker_logs/{document=**} {
      allow read, write: if request.auth != null;
    }
    match /workers/{document=**} {
      allow read, write: if request.auth != null;
    }
    // Development: Allow all (REMOVE FOR PRODUCTION)
    match /{document=**} {
      allow read, write: if request.time < timestamp.date(2025, 12, 31);
    }
  }
}
```

3. Click "Publish"

## Step 6: (Optional) Enable Firebase Authentication

If you want user authentication:

1. Go to "Authentication" in Firebase Console
2. Click "Get Started"
3. Enable "Email/Password" provider
4. Enable "Anonymous" provider (for testing)

## Step 7: Verify Installation

1. Build the project:
```bash
./gradlew clean build
```

2. Run the app and check logcat for Firebase initialization messages:
```
D/WorkWatchApp: Firebase initialized successfully
D/WorkWatchApp: Cloud sync worker scheduled
```

## Firestore Collections

The app uses the following Firestore collections:

### hash_leaks
Stores hash leak information for security tracking.

```json
{
  "workerId": "worker_1",
  "hash": "base64_encoded_hash",
  "timestamp": 1234567890,
  "nonce": "unique_nonce_value"
}
```

### worker_logs
Stores synchronized worker check-in/check-out logs.

```json
{
  "workerId": "worker_1",
  "checkInTime": 1234567890,
  "checkOutTime": 1234571490,
  "latitude": 40.7128,
  "longitude": -74.0060,
  "isSynced": true,
  "timestamp": 1234571500
}
```

### workers
(Optional) Stores worker configuration and metadata.

```json
{
  "workerId": "worker_1",
  "name": "John Doe",
  "email": "john@example.com",
  "createdAt": 1234567890
}
```

## Troubleshooting

### Build Error: google-services.json not found

**Solution:** Make sure the file is in `app/src/google-services.json`, not in `app/` or other directories.

### Firebase not initializing

**Solution:** Check logcat for errors. Make sure `google-services.json` is properly placed and contains correct project ID.

### Firestore connection timeout

**Solution:**
- Check internet connection
- Verify Firestore is enabled in Firebase Console
- Check network rules/firewall

### "Permission denied" errors

**Solution:**
- Review and update Firestore Security Rules
- Ensure authentication is properly configured
- Check that the user has required permissions

## Firebase Services Configured

This app uses the following Firebase services:

- ✅ **Firestore Database** - Cloud storage for logs and leaks
- ✅ **Firebase Analytics** - Usage statistics
- ⚠️ **Firebase Auth** - Optional, for user management
- ⚠️ **Firebase Storage** - Optional, for backup files
- ⚠️ **Cloud Messaging** - Optional, for push notifications

## Development vs Production

### Development Mode
- Uses permissive Firestore rules
- Enables all features for testing
- Not suitable for production

### Production Mode
- Implement proper authentication
- Set strict Firestore rules
- Enable billing (required for production)
- Set up error monitoring
- Configure backups

## Next Steps

1. Download and place `google-services.json` in `app/src/`
2. Build and run the app
3. Verify Firebase initialization in logcat
4. Check that logs start syncing to Firestore
5. Monitor Firestore Database for incoming data

## Additional Resources

- [Firebase Documentation](https://firebase.google.com/docs)
- [Firestore Documentation](https://firebase.google.com/docs/firestore)
- [Firebase Security Rules](https://firebase.google.com/docs/firestore/security/get-started)

## Support

For Firebase issues, consult:
- Firebase Console error messages
- Android Studio logcat
- Firebase documentation
- Stack Overflow: `firebase` + `android` tags

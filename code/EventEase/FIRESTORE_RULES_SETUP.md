# Firestore Security Rules Setup for Admin Functionality

## Problem
When trying to view profiles in the admin section, you're getting a "Permission denied" error. This is because Firestore security rules need to allow users with the "admin" role to read all user documents.

## Solution

You need to update your Firestore security rules in the Firebase Console to allow admins to:
1. Read all documents in the `users` collection
2. Delete documents in the `users` collection

## Steps to Fix

### Option 1: Update Rules in Firebase Console (Recommended)

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Navigate to **Firestore Database** → **Rules** tab
4. Copy the rules from the `firestore.rules` file in this project
5. Paste them into the Firebase Console rules editor
6. Click **Publish** to deploy the rules

### Option 2: Deploy Rules Using Firebase CLI

If you have Firebase CLI installed:

```bash
# Install Firebase CLI (if not already installed)
npm install -g firebase-tools

# Login to Firebase
firebase login

# Initialize Firebase (if not already done)
firebase init firestore

# Deploy rules
firebase deploy --only firestore:rules
```

## Key Rules for Admin Functionality

The important rules for admin profile management are:

```javascript
// Helper function to check if user has admin role
function isAdmin() {
  return isAuthenticated() && 
         exists(/databases/$(database)/documents/users/$(request.auth.uid)) &&
         get(/databases/$(database)/documents/users/$(request.auth.uid)).data.roles != null &&
         'admin' in get(/databases/$(database)/documents/users/$(request.auth.uid)).data.roles;
}

// Users collection
match /users/{userId} {
  // Users can read their own document OR admins can read any user document
  // This allows admins to query the entire collection (required for listing all profiles)
  allow read: if isOwner(userId) || isAdmin();
  
  // Users can write their own document
  allow write: if isOwner(userId);
  
  // Admins can delete any user document
  allow delete: if isAdmin();
}
```

**Important**: The key change is using `allow read: if isOwner(userId) || isAdmin();` which allows admins to read ALL user documents, enabling collection queries like `collection("users").get()`.

## Verify Admin Account Setup

Make sure your admin account has the `admin` role in Firestore:

1. Go to Firestore Database → `users` collection
2. Find your admin user document (by UID)
3. Ensure it has a `roles` field that is an array containing `"admin"`:
   ```json
   {
     "email": "admin@example.com",
     "name": "Admin User",
     "roles": ["admin"],
     ...
   }
   ```

## Testing

After updating the rules:

1. Wait a few seconds for rules to propagate
2. Log in as an admin user
3. Navigate to the Admin → Profiles section
4. You should now be able to see all user profiles

## Troubleshooting

- **Still getting permission denied?**
  - Verify your user document has `roles: ["admin"]` in Firestore
  - Check that the rules were successfully published (check the Rules tab timestamp)
  - Wait a few minutes for rules to propagate globally

- **Rules not deploying?**
  - Check Firebase Console for any syntax errors
  - Verify you have the correct permissions on the Firebase project
  - Try using Firebase CLI for more detailed error messages

## Security Note

These rules allow users with the "admin" role to:
- Read all user profiles
- Delete any user profile
- Read all events and images
- Delete events and images

Make sure only trusted users are assigned the "admin" role!

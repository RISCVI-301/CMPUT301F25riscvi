# Firebase Firestore Rules Setup

## Required Collections & Rules

You need to add rules for the **`admitted`** collection in Firebase Console.

### Step 1: Go to Firebase Console
1. Open your Firebase project
2. Go to **Firestore Database**
3. Click on **Rules** tab

### Step 2: Add These Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Existing events collection
    match /events/{eventId} {
      allow read: if true;
      allow write: if request.auth != null;
    }
    
    // Existing waitlists collection
    match /waitlists/{waitlistId} {
      allow read: if true;
      allow write: if request.auth != null;
    }
    
    // NEW: Admitted collection for accepted invitations
    match /admitted/{admittedId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
      allow create: if request.auth != null;
      allow update: if request.auth != null;
    }
    
    // Existing users collection (if you have one)
    match /users/{userId} {
      allow read: if true;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

### Step 3: Publish the Rules
Click **Publish** to apply the rules.

## How It Works

### Document Structure in `admitted` collection:
- **Document ID**: `{eventId}_{uid}` (e.g., "e1_demo-uid-123")
- **Fields**:
  - `eventId`: String - ID of the event
  - `uid`: String - User ID who accepted
  - `admittedAt`: Number (timestamp) - When user was admitted
  - `acceptedAt`: Number (timestamp) - When user accepted invitation

### Flow:
1. User accepts invitation → Creates document in `admitted` collection
2. MyEventsFragment checks if user is admitted → Filters out accepted events
3. UpcomingEventsFragment queries `admitted` collection → Shows accepted events

## Testing the Setup

After setting the rules, test by:
1. Opening the app
2. Going to My Events
3. Clicking an event with an invitation (teal dot)
4. Clicking "Register" → "Done"
5. Check Firebase Console → Should see new document in `admitted` collection
6. Return to My Events → Event should be gone
7. Go to Upcoming Events → Event should appear there

## Troubleshooting

If it doesn't work, check Logcat for these messages:
- `AdmittedRepository`: "Attempting to admit user..."
- `InvitationRepository`: "Accept called with..."
- Look for "❌ FAILED" or "⚠️" messages indicating errors


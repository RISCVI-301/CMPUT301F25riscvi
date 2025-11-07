# Admin Profile Access - Debugging Guide

## Issue
Getting "Permission denied" error when trying to view profiles in the admin section.

## Possible Causes

### 1. Firestore Rules Not Deployed
The rules in `firestore.rules` need to be deployed to Firebase Console.

**Solution:**
- Go to Firebase Console → Firestore Database → Rules
- Copy the contents of `firestore.rules`
- Paste and click **Publish**

### 2. Admin User Document Missing `roles` Field
The admin user document in Firestore must have a `roles` array field with `"admin"` in it.

**Check in Firebase Console:**
- Go to Firestore Database → `users` collection
- Find your admin user document (by UID)
- Verify it has: `roles: ["admin"]` (array field, not string)

**If missing, add it:**
```json
{
  "email": "admin@example.com",
  "name": "Admin User",
  "roles": ["admin"],  // ← Make sure this exists as an array
  ...
}
```

### 3. Circular Dependency in Firestore Rules
The `isAdmin()` function reads the user document, but the read permission might be blocking it.

**Current rule:**
```javascript
allow read: if isOwner(userId) || isAdmin();
```

This should work because users can read their own document via `isOwner(userId)`, but let's verify.

### 4. Collection Query Permissions
When querying `db.collection("users").get()`, Firestore checks permissions for EACH document individually.

**The issue:** If the user's own document can be read (via `isOwner`), but other users' documents fail the `isAdmin()` check, the query will fail.

## Diagnostic Steps

### Step 1: Check Logcat
Run the app and check Logcat for detailed error messages:
```
adb logcat | grep -E "AdminProfile|AdminProfileDB"
```

Look for:
- "Failed to verify admin status" - means the initial admin check failed
- "Failed to read users collection" - means the collection query failed
- Permission errors with specific details

### Step 2: Verify Admin Role in Firestore
1. Go to Firebase Console
2. Open Firestore Database
3. Navigate to `users` collection
4. Find your user document (by UID from Firebase Auth)
5. Verify the `roles` field exists and contains `"admin"`

### Step 3: Test Firestore Rules
In Firebase Console → Firestore Database → Rules, you can test rules:
- Use the Rules Playground
- Simulate a read request to `/users/{yourUserId}`
- Check if `isAdmin()` returns true

### Step 4: Verify Rules Are Deployed
1. Check the Rules tab in Firebase Console
2. Look at the "Last published" timestamp
3. Make sure it's recent (after you updated the rules)

## Quick Fix: Simplified Rules (Temporary Test)

If the issue persists, try this simplified rule to test:

```javascript
match /users/{userId} {
  // Allow users to read their own document
  allow read: if request.auth != null && request.auth.uid == userId;
  
  // TEMPORARY: Allow all authenticated users to read all user documents (for testing only!)
  // REMOVE THIS AFTER TESTING - it's a security risk!
  allow read: if request.auth != null;
  
  allow write: if request.auth != null && request.auth.uid == userId;
  allow delete: if request.auth != null && request.auth.uid == userId;
}
```

If this works, the issue is with the `isAdmin()` function in the rules. If it doesn't work, there's a different issue.

## Expected Behavior

1. User logs in as admin
2. `UserRoleChecker.isAdmin()` reads `/users/{userId}` document
3. Checks if `roles` array contains `"admin"`
4. If admin, calls `fetchAllProfiles()`
5. `fetchAllProfiles()` does `db.collection("users").get()`
6. Firestore rules check: for each document, `isOwner(userId) || isAdmin()`
7. For admin's own document: `isOwner(userId)` = true ✓
8. For other documents: `isAdmin()` should be true ✓
9. All documents returned successfully

## Common Issues

### Issue: "Failed to verify admin status"
- **Cause:** Cannot read user's own document
- **Fix:** Check if user document exists and has correct permissions

### Issue: "Permission denied" on collection query
- **Cause:** `isAdmin()` function in rules is not working correctly
- **Fix:** Verify the `isAdmin()` function can successfully read the user document

### Issue: User has admin role but still gets permission denied
- **Cause:** Rules not deployed or `roles` field format incorrect
- **Fix:** Deploy rules and verify `roles: ["admin"]` (array, not string)

## Next Steps

After checking Logcat, you'll see which step is failing. Based on the error:

1. **"Failed to verify admin status"** → Check user document and roles field
2. **"Permission denied" on collection query** → Check Firestore rules deployment
3. **"Only administrators can view all profiles"** → User doesn't have admin role


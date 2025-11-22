# EventEase Testing Guide

This guide explains how to test the automatic selection, invitation, and notification features.

## Prerequisites

1. **Firebase Setup:**
   - Cloud Functions deployed (for push notifications)
   - FCM configured in Firebase Console
   - Firestore database accessible

2. **Android Device/Emulator:**
   - Android 13+ for notification permission testing
   - At least 2-3 test accounts (organizer + entrants)

3. **Firebase Console Access:**
   - To view Firestore data
   - To check Cloud Functions logs

## Testing Flow Overview

### 1. Create Event with Sample Size

**Steps:**
1. Log in as an **Organizer**
2. Go to "Create New Event"
3. Fill in event details:
   - Event name, description, location
   - **Registration Start**: Set to a time in the past (or current time)
   - **Registration End**: Set to a time in the past (or very soon)
   - **Deadline to Accept/Decline**: Set to a future time (e.g., 1 hour from now)
   - **Event Start Date**: Set to a future time
4. **Waiting List Capacity:**
   - Choose "Any Number" or "Specific Number"
   - If "Specific Number", enter a capacity (e.g., 50)
5. **Sample Size for Initial Invitation**: 
   - **Always visible** - Enter a number (e.g., 20)
   - This is the number of invitations that will be sent automatically
6. Upload poster and save event

**Expected Result:**
- Event created successfully
- Event document in Firestore has `sampleSize` field
- `selectionProcessed` field is `false` (or doesn't exist)

### 2. Join Waitlist (As Entrants)

**Steps:**
1. Log in as **Entrant 1, 2, 3...** (create multiple test accounts)
2. Go to Discover tab
3. Find the event you just created
4. Click "Join Waitlist"
5. Repeat for multiple entrants (at least 3-5 for testing)

**Expected Result:**
- Entrants appear in `events/{eventId}/WaitlistedEntrants` subcollection
- Waitlist count increases

### 3. Trigger Automatic Selection

**How it works:**
- Selection happens automatically when:
  1. Registration period has ended (`registrationEnd < currentTime`)
  2. Event is opened in `OrganizerWaitlistActivity` or `OrganizerViewEntrantsActivity`
  3. `selectionProcessed` is `false`

**Steps to Trigger:**
1. Ensure registration period has ended
2. As **Organizer**, open the event's waitlist page or view entrants page
3. Check Logcat for logs:
   ```
   EventSelectionHelper: === Starting selection check for event: {eventId} ===
   EventSelectionHelper: Randomly selecting {sampleSize} out of {total} waitlisted entrants
   EventSelectionHelper: ✓ Successfully moved {count} entrants to SelectedEntrants
   EventSelectionHelper: ✓ Successfully sent {count} invitations with push notifications
   ```

**Expected Result:**
- `sampleSize` number of entrants moved from `WaitlistedEntrants` to `SelectedEntrants`
- Invitation documents created in `invitations` collection
- Push notifications sent to selected entrants
- Event document has `selectionProcessed: true`

### 4. Test Push Notifications

**Steps:**
1. As a **Selected Entrant**, ensure notification permission is granted
2. Close the app completely (or keep it in background)
3. Wait for notification (should arrive within seconds)
4. Check notification:
   - Title: "You've been selected! 🎉"
   - Body: Includes event name and deadline
   - Click notification → Should open event detail page

**Expected Result:**
- Notification appears even when app is closed
- Notification shows correct event name and deadline
- Clicking notification opens `EventDetailsDiscoverActivity` with event details

### 5. Test Invitation Acceptance/Decline

**Accept Invitation:**
1. As **Selected Entrant**, open the app
2. Go to "My Events" → Should see invitation
3. Click "Accept"
4. Check Firestore:
   - Invitation status: `ACCEPTED`
   - User stays in `SelectedEntrants` subcollection
   - User also in `AdmittedEntrants` subcollection

**Decline Invitation:**
1. As another **Selected Entrant**, open invitation
2. Click "Decline"
3. Check Firestore:
   - Invitation status: `DECLINED`
   - User moved from `SelectedEntrants` to `CancelledEntrants`
   - Replacement process may trigger (if implemented)

### 6. Test Deadline Processing

**Steps:**
1. Wait until deadline passes (`deadlineEpochMs < currentTime`)
2. As **Organizer**, open waitlist or view entrants page
3. Check Logcat for:
   ```
   InvitationDeadlineProcessor: === Processing deadline for event: {eventId} ===
   InvitationDeadlineProcessor: Moving {count} non-responders to CancelledEntrants
   EventSelectionHelper: Moving {count} remaining waitlisted entrants to NonSelectedEntrants
   ```

**Expected Result:**
- Non-responders (PENDING invitations) moved to `CancelledEntrants`
- Invitation status updated to `DECLINED`
- Remaining waitlisted entrants moved to `NonSelectedEntrants`

### 7. Test Replacement Flow (If Implemented)

**Steps:**
1. After an entrant declines, check if replacement is automatically selected
2. Replacement should:
   - Come from remaining `WaitlistedEntrants`
   - Receive invitation and notification
   - Move to `SelectedEntrants` if they accept

## Verification Checklist

### Firestore Collections to Check:

1. **Events Collection:**
   - `events/{eventId}`:
     - `sampleSize`: Should match entered value
     - `selectionProcessed`: Should be `true` after selection
     - `capacity`: Waitlist capacity (or -1 for unlimited)

2. **Event Subcollections:**
   - `events/{eventId}/WaitlistedEntrants`: Should be empty after selection (or contain remaining)
   - `events/{eventId}/SelectedEntrants`: Should contain selected users
   - `events/{eventId}/CancelledEntrants`: Should contain declined/non-responders
   - `events/{eventId}/NonSelectedEntrants`: Should contain remaining waitlisted after deadline

3. **Invitations Collection:**
   - `invitations/{invitationId}`:
     - `status`: PENDING, ACCEPTED, or DECLINED
     - `eventId`: Should match event
     - `uid`: User ID
     - `expiresAt`: Should match deadline

4. **Notification Requests:**
   - `notificationRequests/{requestId}`:
     - `status`: SENT, PENDING, or ERROR
     - `userIds`: List of user IDs notified
     - `processed`: Should be `true` after processing

## Common Issues & Solutions

### Issue: Selection not happening
**Solution:**
- Check if `registrationEnd` has passed
- Check if `selectionProcessed` is already `true`
- Check Logcat for errors
- Verify `sampleSize` is set and > 0

### Issue: Notifications not received
**Solution:**
- Check notification permission (Android 13+)
- Verify FCM token exists in `users/{uid}/fcmToken`
- Check Cloud Functions logs for errors
- Verify `notificationRequests` document was created
- Check device is connected to internet

### Issue: Notification doesn't open event
**Solution:**
- Verify `eventId` is in notification data
- Check `MainActivity.handleExternalNav()` is called
- Verify `EventDetailsDiscoverActivity` receives `EXTRA_EVENT_ID`

### Issue: Sample size field not visible
**Solution:**
- Check layout file has `android:visibility` removed (not "gone")
- Verify `tvSampleSize` and `etSampleSize` are not hidden in Activity code

## Testing with Multiple Devices

For best testing:
1. **Device 1**: Organizer account
2. **Device 2-5**: Different entrant accounts
3. Use Firebase Console to monitor:
   - Firestore data changes
   - Cloud Functions execution logs
   - FCM message delivery

## Logcat Filters

Use these filters to see relevant logs:
```
EventSelectionHelper
InvitationDeadlineProcessor
NotificationHelper
EventNotificationService
FCMTokenManager
```

## Quick Test Scenario

**Simple 3-person test:**
1. Create event with `sampleSize = 1`
2. 3 entrants join waitlist
3. Registration ends → 1 randomly selected
4. Selected entrant receives notification
5. Selected entrant accepts → stays in SelectedEntrants
6. Deadline passes → remaining 2 move to NonSelectedEntrants

This validates the entire flow with minimal setup.


# ✅ Updated Decline Implementation - Correct Behavior

## 🔄 **NEW Decline Behavior (As Per Requirements)**

### **When User Clicks "Decline":**
1. ✅ Invitation status → `DECLINED` in Firebase
2. ✅ **User REMAINS in waitlist** (not removed)
3. ✅ Teal invitation dot removed from My Events view
4. ✅ User can be **re-invited** in future lottery rerolls
5. ✅ Waitlist count **NOT decremented** (user still counted)

### **What This Means:**
- Declining an invitation is like saying "not right now"
- User stays in the lottery pool for future draws
- Organizer can re-invite the same user if needed
- User's spot on the waitlist is preserved

---

## 📊 **Waitlist Count - Now Working!**

### **Fixes Applied:**

#### 1. **Real-time Listener in EventDetailActivity**
   - Added `setupWaitlistCountListener()` method
   - Listens to Firebase for count updates
   - Updates UI automatically when count changes
   - Shows "Loading..." initially, then real count

#### 2. **Query Actual Waitlist Documents**
   - `FirebaseEventRepository.queryWaitlistCount()` added
   - Queries `/waitlists` collection filtered by `eventId`
   - Counts actual documents (source of truth)
   - Updates cached count and notifies listeners
   - Syncs count back to event document

#### 3. **Auto-sync on Join/Accept**
   - When user joins waitlist → count increments
   - When user accepts invitation → count stays same (user still in waitlist until event happens)
   - Count always reflects actual number of users in waitlist

---

## 🔧 **Files Modified**

### 1. **EventDetailActivity.java**
```java
// NEW: Real-time waitlist count listener
private void setupWaitlistCountListener() {
    waitlistCountReg = eventRepo.listenWaitlistCount(eventId, count -> {
        tvWaitlistCount.setText(String.valueOf(count));
    });
}

// UPDATED: Decline only changes invitation status, keeps user in waitlist
private void declineInvitation() {
    invitationRepo.decline(invitationId, eventId, uid)
        .addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Invitation declined. You remain on the waitlist.", 
                          Toast.LENGTH_LONG).show();
            finish();
        });
}
```

### 2. **FirebaseEventRepository.java**
```java
// NEW: Query real waitlist count from Firebase
private void queryWaitlistCount(String eventId) {
    db.collection("waitlists")
        .whereEqualTo("eventId", eventId)
        .get()
        .addOnSuccessListener(querySnapshot -> {
            int actualCount = querySnapshot.size();
            waitlistCounts.put(eventId, actualCount);
            
            // Sync count to event document
            db.collection("events").document(eventId)
                .update("waitlistCount", actualCount);
            
            notifyCount(eventId);
        });
}
```

### 3. **FirebaseInvitationRepository.java**
- No changes needed - decline already updates status correctly

### 4. **FirebaseWaitlistRepository.java**
- `leave()` method still exists for manual waitlist removal
- Just not called during decline flow

---

## 🎯 **User Flow - Decline Invitation**

1. **User opens event** from My Events (has teal dot = invited)
2. **Sees Register & Decline buttons**
3. **Clicks Decline** → Confirmation dialog
4. **Clicks Done** in dialog
5. **Backend**: Invitation status → `DECLINED` in Firebase
6. **Backend**: User stays in `/waitlists/{eventId_uid}` (NOT deleted)
7. **UI**: Toast shows "Invitation declined. You remain on the waitlist."
8. **Returns to My Events**: Teal dot gone, event still shows (user in waitlist)
9. **Waitlist count**: Unchanged (user still counted)
10. **Future lottery**: User can be drawn again and re-invited

---

## 🎯 **User Flow - Accept Invitation**

1. **User opens event** from My Events (has teal dot = invited)
2. **Clicks Register** → Confirmation dialog
3. **Clicks Done**
4. **Backend**: 
   - Invitation status → `ACCEPTED`
   - User added to `/admitted/{eventId_uid}`
   - User REMAINS in `/waitlists` (for record keeping)
5. **UI**: Event disappears from My Events (filtered out by `admitted = true`)
6. **Event appears in Upcoming Events**
7. **Waitlist count**: Unchanged (user still technically in waitlist)

---

## 📊 **Firebase Collections State**

### **After User Joins Waitlist:**
```javascript
// /waitlists/{eventId_uid}
{
  eventId: "e3",
  uid: "gN8jla0HwJdMT45SMzvHsNkiLPT2",
  joinedAt: 1730000000000
}

// /events/e3
{
  waitlistCount: 5  // Incremented
}
```

### **After Organizer Sends Invitation:**
```javascript
// /invitations/i3
{
  eventId: "e3",
  uid: "gN8jla0HwJdMT45SMzvHsNkiLPT2",
  status: "PENDING",
  issuedAt: 1730050000000
}

// /waitlists/{eventId_uid} - STILL EXISTS
// /events/e3 - waitlistCount: 5 (unchanged)
```

### **After User DECLINES Invitation:**
```javascript
// /invitations/i3
{
  status: "DECLINED",  // Changed
  declinedAt: 1730060000000  // Added
}

// /waitlists/{eventId_uid} - STILL EXISTS ✅
// /events/e3 - waitlistCount: 5 (unchanged) ✅
```

### **After User ACCEPTS Invitation:**
```javascript
// /invitations/i3
{
  status: "ACCEPTED",
  acceptedAt: 1730070000000
}

// /admitted/{eventId_uid} - NEW DOCUMENT
{
  eventId: "e3",
  uid: "gN8jla0HwJdMT45SMzvHsNkiLPT2",
  admittedAt: 1730070000000
}

// /waitlists/{eventId_uid} - STILL EXISTS
// /events/e3 - waitlistCount: 5 (unchanged)
```

---

## 🧪 **Testing Checklist**

### ✅ Test Decline Flow:
1. Login as `gamestari734@gmail.com`
2. Go to My Events → See 4 events with teal dots
3. Click "Live Jazz Concert" event
4. **Verify**: Waitlist count shows actual number (not 0)
5. Click "Decline" button
6. Click "Done" in dialog
7. **Verify**: Toast says "You remain on the waitlist"
8. Return to My Events
9. **Verify**: "Live Jazz Concert" still appears (no teal dot)
10. **Verify**: Firebase `/waitlists` still has your entry
11. **Verify**: Firebase `/invitations/i5` has `status: "DECLINED"`

### ✅ Test Waitlist Count:
1. Open any event detail page
2. **Verify**: Count shows "Loading..." briefly
3. **Verify**: Count updates to actual number from Firebase
4. Join a waitlist from Discover page
5. **Verify**: Count increments immediately
6. Check Logcat for: `"Queried waitlist count for event: X"`

### ✅ Test Accept Flow:
1. Open invited event
2. Click "Register" → Done
3. **Verify**: Toast says "Event added to upcoming"
4. **Verify**: Event disappears from My Events
5. **Verify**: Event appears in Upcoming Events
6. **Verify**: Waitlist count unchanged

---

## 🐛 **Troubleshooting**

### Waitlist count shows 0:
- Check Logcat for `"Queried waitlist count for event: X"`
- Verify `/waitlists` collection exists in Firebase
- Verify documents have `eventId` field
- Check Firebase Rules allow reading `/waitlists`

### Declined event disappears from My Events:
- This is CORRECT behavior if user is not in waitlist
- Check Firebase `/waitlists/{eventId_uid}` - should exist
- Check `MyEventsFragment` filtering logic
- Verify `onResume()` is being called

### Can't decline invitation:
- Check Logcat for error messages
- Verify Firebase Rules allow updating `/invitations`
- Verify `invitationId` is not null

---

## 📝 **Summary of Changes**

### What Changed from Previous Implementation:
❌ **REMOVED**: `waitlistRepo.leave()` call from `declineInvitation()`  
❌ **REMOVED**: Waitlist count decrement on decline  
✅ **ADDED**: Real-time waitlist count listener  
✅ **ADDED**: Query actual waitlist documents for accurate count  
✅ **UPDATED**: Decline message to clarify user remains on waitlist  

### What Stayed the Same:
✅ Invitation status → DECLINED in Firebase  
✅ Teal dot removed from My Events view  
✅ Accept flow (adds to admitted, moves to Upcoming)  
✅ Firebase Rules requirements  

---

**Build Status**: ✅ Compiled successfully  
**Date**: November 4, 2025  
**Ready for Testing**: ✅ YES


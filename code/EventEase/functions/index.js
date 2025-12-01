const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

/**
 * Cloud Function triggered when a notification request is created in Firestore.
 * This function sends FCM push notifications to all users specified in the request.
 */
exports.sendNotification = functions.firestore
    .document('notificationRequests/{requestId}')
    .onCreate(async (snap, context) => {
        const requestData = snap.data();
        const requestId = context.params.requestId;
        
        console.log(`\n=== sendNotification TRIGGERED ===`);
        console.log(`Request ID: ${requestId}`);
        console.log(`Triggered at: ${new Date().toISOString()}`);
        console.log(`Request data keys: ${Object.keys(requestData).join(', ')}`);
        
        // Skip if already processed
        if (requestData.processed === true) {
            console.log(`⚠ Request ${requestId} already processed, skipping`);
            return null;
        }
        
        const { userIds, title, message, eventId, eventTitle, groupType } = requestData;
        
        if (!userIds || !Array.isArray(userIds) || userIds.length === 0) {
            console.error(`✗ Request ${requestId} has no userIds, marking as processed`);
            await snap.ref.update({ processed: true, error: 'No userIds provided' });
            return null;
        }
        
        console.log(`=== Processing notification request ${requestId} ===`);
        console.log(`Title: ${title}`);
        console.log(`Message: ${message}`);
        console.log(`Event ID: ${eventId}`);
        console.log(`Event Title: ${eventTitle || 'N/A'}`);
        console.log(`Group Type: ${groupType}`);
        console.log(`User IDs: ${JSON.stringify(userIds)}`);
        console.log(`Processing for ${userIds.length} users`);
        
            // CRITICAL: For selection notifications, verify these users are actually in SelectedEntrants
            if (groupType === 'selection') {
                console.log(`⚠ VERIFICATION: This is a SELECTION notification. Verifying users are in SelectedEntrants...`);
                const selectedSnapshot = await admin.firestore()
                    .collection('events').doc(eventId)
                    .collection('SelectedEntrants')
                    .get();
                const actualSelectedIds = selectedSnapshot.docs.map(doc => doc.id);
                console.log(`  SelectedEntrants contains: ${JSON.stringify(actualSelectedIds)}`);
                
                const notInSelected = userIds.filter(id => !actualSelectedIds.includes(id));
                if (notInSelected.length > 0) {
                    console.error(`⚠ CRITICAL ERROR: ${notInSelected.length} users in notification request are NOT in SelectedEntrants: ${JSON.stringify(notInSelected)}`);
                    console.error(`  These users should NOT receive selection notifications!`);
                } else {
                    console.log(`  ✓ All ${userIds.length} users in notification request are verified to be in SelectedEntrants`);
                }
            }
        
        try {
            // Fetch FCM tokens for all users
            const userDocs = await Promise.all(
                userIds.map(userId => 
                    admin.firestore().collection('users').doc(userId).get()
                )
            );
            
            // Helper function to get user's first name
            const getUserFirstName = (userData) => {
                if (!userData) return null;
                
                // Try firstName first
                if (userData.firstName && userData.firstName.trim()) {
                    return userData.firstName.trim();
                }
                
                // Try fullName and extract first word
                if (userData.fullName && userData.fullName.trim()) {
                    const firstWord = userData.fullName.trim().split(' ')[0];
                    if (firstWord) return firstWord;
                }
                
                // Try name and extract first word
                if (userData.name && userData.name.trim()) {
                    const firstWord = userData.name.trim().split(' ')[0];
                    if (firstWord) return firstWord;
                }
                
                return null;
            };
            
            // Helper function to personalize message
            const personalizeMessage = (originalMessage, firstName) => {
                if (!firstName) {
                    return originalMessage || 'You have an update regarding an event.';
                }
                
                // Capitalize first letter
                const capitalizedName = firstName.charAt(0).toUpperCase() + firstName.slice(1).toLowerCase();
                return `Hey ${capitalizedName}, ${originalMessage || 'You have an update regarding an event.'}`;
            };
            
            // Prepare user data with tokens and personalized messages
            const userNotifications = [];
            const usersWithoutTokens = [];
            
            userDocs.forEach((doc, index) => {
                if (doc.exists) {
                    const userData = doc.data();
                    const fcmToken = userData.fcmToken;
                    
                    // Check notificationsEnabled - handle both boolean and string types
                    let notificationsEnabled = true; // Default to true
                    const notificationsEnabledRaw = userData.notificationsEnabled;
                    if (notificationsEnabledRaw !== undefined && notificationsEnabledRaw !== null) {
                        if (typeof notificationsEnabledRaw === 'boolean') {
                            notificationsEnabled = notificationsEnabledRaw;
                        } else if (typeof notificationsEnabledRaw === 'string') {
                            notificationsEnabled = notificationsEnabledRaw.toLowerCase() === 'true';
                        } else {
                            // Try to convert other types
                            notificationsEnabled = Boolean(notificationsEnabledRaw);
                        }
                    }
                    
                    // CRITICAL: Also check specific notification preferences based on groupType
                    // This ensures users who have disabled specific notification types don't receive them
                    let preferenceEnabled = true; // Default to enabled
                    if (groupType === 'selected' || groupType === 'selection') {
                        // For selected/invited notifications, check notificationPreferenceInvited
                        const invitedPref = userData.notificationPreferenceInvited;
                        if (invitedPref !== undefined && invitedPref !== null) {
                            if (typeof invitedPref === 'boolean') {
                                preferenceEnabled = invitedPref;
                            } else if (typeof invitedPref === 'string') {
                                preferenceEnabled = invitedPref.toLowerCase() === 'true';
                            } else {
                                preferenceEnabled = Boolean(invitedPref);
                            }
                        }
                        // If not set, default to true (enabled)
                    } else if (groupType === 'nonSelected' || groupType === 'sorry') {
                        // For not-invited/sorry notifications, check notificationPreferenceNotInvited
                        const notInvitedPref = userData.notificationPreferenceNotInvited;
                        if (notInvitedPref !== undefined && notInvitedPref !== null) {
                            if (typeof notInvitedPref === 'boolean') {
                                preferenceEnabled = notInvitedPref;
                            } else if (typeof notInvitedPref === 'string') {
                                preferenceEnabled = notInvitedPref.toLowerCase() === 'true';
                            } else {
                                preferenceEnabled = Boolean(notInvitedPref);
                            }
                        }
                        // If not set, default to true (enabled)
                    }
                    // For other groupTypes (waitlist, cancelled, general), don't check specific preferences
                    
                    console.log(`User ${userIds[index]}:`);
                    console.log(`  - Token exists: ${!!fcmToken}`);
                    console.log(`  - Token length: ${fcmToken ? fcmToken.length : 0}`);
                    console.log(`  - notificationsEnabled (raw): ${notificationsEnabledRaw} (type: ${typeof notificationsEnabledRaw})`);
                    console.log(`  - notificationsEnabled (parsed): ${notificationsEnabled}`);
                    console.log(`  - Group type: ${groupType}`);
                    console.log(`  - Preference enabled: ${preferenceEnabled}`);
                    
                    // User must have both notificationsEnabled AND the specific preference enabled
                    if (fcmToken && notificationsEnabled && preferenceEnabled) {
                        const firstName = getUserFirstName(userData);
                        const personalizedMessage = personalizeMessage(message, firstName);
                        
                        userNotifications.push({
                            token: fcmToken,
                            userId: userIds[index],
                            firstName: firstName,
                            personalizedMessage: personalizedMessage
                        });
                        
                        console.log(`✓ Added token for user ${userIds[index]} (firstName: ${firstName || 'N/A'}, token preview: ${fcmToken.substring(0, 20)}...)`);
                    } else {
                        let reason = '';
                        if (!fcmToken) {
                            reason = 'no FCM token';
                        } else if (!notificationsEnabled) {
                            reason = 'notifications disabled in Firestore';
                        } else if (!preferenceEnabled) {
                            reason = `notification preference disabled for ${groupType} notifications`;
                        }
                        console.log(`✗ User ${userIds[index]}: ${reason}`);
                        
                        if (!fcmToken && notificationsEnabled && preferenceEnabled) {
                            usersWithoutTokens.push({
                                userId: userIds[index],
                                deviceId: userIds[index]
                            });
                        }
                    }
                } else {
                    console.log(`✗ User ${userIds[index]} not found in Firestore`);
                }
            });
            
            // Send FCM notifications if we have tokens
            let fcmSuccessCount = 0;
            let fcmFailureCount = 0;
            const failedUsers = []; // Track users with failed notifications for retry
            const invalidTokens = []; // Track invalid tokens that should be removed
            
            if (userNotifications.length > 0) {
            const messages = userNotifications.map(userNotif => {
                return {
                    token: userNotif.token,
                    notification: {
                        title: title || 'Event Update',
                        body: userNotif.personalizedMessage,
                    },
                    data: {
                        type: groupType || 'general',
                        eventId: eventId || '',
                        eventTitle: eventTitle || 'Event',
                        title: title || 'Event Update',
                        message: userNotif.personalizedMessage,
                        click_action: 'FLUTTER_NOTIFICATION_CLICK',
                    },
                    android: {
                        priority: 'high',
                        notification: {
                            channelId: 'event_invitations',
                            sound: 'default',
                            priority: 'high',
                        },
                    },
                    apns: {
                        payload: {
                            aps: {
                                sound: 'default',
                                badge: 1,
                            },
                        },
                    },
                };
            });
            
            const batchSize = 500;
            
                console.log(`Sending personalized FCM notifications to ${userNotifications.length} users`);
            for (let i = 0; i < messages.length; i += batchSize) {
                const batch = messages.slice(i, i + batchSize);
                
                try {
                    console.log(`Sending batch ${Math.floor(i / batchSize) + 1} to ${batch.length} users`);
                    const response = await admin.messaging().sendEach(batch);
                    
                    console.log(`Batch ${Math.floor(i / batchSize) + 1} result: ${response.successCount} sent, ${response.failureCount} failed`);
                    
                        fcmSuccessCount += response.successCount;
                        fcmFailureCount += response.failureCount;
                    
                    // Enhanced error logging and tracking
                    if (response.failureCount > 0) {
                        response.responses.forEach((resp, idx) => {
                            if (!resp.success) {
                                const userNotif = userNotifications[i + idx];
                                const userId = userNotif.userId;
                                const error = resp.error;
                                
                                // Detailed error logging
                                console.error(`✗ Failed to send to user ${userId} (${userNotif.firstName || 'N/A'}):`);
                                if (error) {
                                    console.error(`  Error code: ${error.code || 'UNKNOWN'}`);
                                    console.error(`  Error message: ${error.message || 'No message'}`);
                                    console.error(`  Error details: ${JSON.stringify(error)}`);
                                    
                                    // Track failed users for retry (only for retryable errors)
                                    const retryableErrors = [
                                        'messaging/unavailable',
                                        'messaging/internal-error',
                                        'messaging/server-unavailable',
                                        'unavailable',
                                        'internal',
                                        'deadline-exceeded'
                                    ];
                                    
                                    const isRetryable = retryableErrors.some(code => 
                                        error.code && error.code.includes(code)
                                    );
                                    
                                    if (isRetryable) {
                                        failedUsers.push({
                                            userId: userId,
                                            token: userNotif.token,
                                            firstName: userNotif.firstName,
                                            error: error.code,
                                            retryCount: 0
                                        });
                                        console.log(`  → Marked for retry (retryable error: ${error.code})`);
                                    }
                                    
                                    // Track invalid tokens (should be removed from user document)
                                    const invalidTokenErrors = [
                                        'messaging/invalid-registration-token',
                                        'messaging/registration-token-not-registered',
                                        'invalid-argument',
                                        'not-found'
                                    ];
                                    
                                    if (invalidTokenErrors.some(code => 
                                        error.code && error.code.includes(code)
                                    )) {
                                        invalidTokens.push({
                                            userId: userId,
                                            token: userNotif.token,
                                            error: error.code
                                        });
                                        console.warn(`  → Invalid token detected, will be removed from user document`);
                                    }
                                } else {
                                    console.error(`  No error details available`);
                                }
                            } else {
                                const userId = userNotifications[i + idx].userId;
                                const firstName = userNotifications[i + idx].firstName || 'N/A';
                                console.log(`✓ Successfully sent personalized notification to user ${userId} (${firstName})`);
                            }
                        });
                    } else {
                        console.log(`✓ All ${batch.length} personalized notifications in batch sent successfully`);
                    }
                } catch (error) {
                    console.error(`Error sending batch ${Math.floor(i / batchSize) + 1}:`, error);
                    console.error(`  Error type: ${error.constructor.name}`);
                    console.error(`  Error message: ${error.message}`);
                    console.error(`  Error stack: ${error.stack}`);
                        fcmFailureCount += batch.length;
                    
                    // Mark all users in this batch for retry (batch-level error)
                    for (let j = i; j < Math.min(i + batchSize, userNotifications.length); j++) {
                        failedUsers.push({
                            userId: userNotifications[j].userId,
                            token: userNotifications[j].token,
                            firstName: userNotifications[j].firstName,
                            error: 'batch-error',
                            retryCount: 0
                        });
                    }
                }
                }
            }
            
            // Remove invalid tokens from user documents
            if (invalidTokens.length > 0) {
                console.log(`Removing ${invalidTokens.length} invalid FCM tokens from user documents`);
                const tokenCleanupBatch = admin.firestore().batch();
                for (const invalidToken of invalidTokens) {
                    const userRef = admin.firestore().collection('users').doc(invalidToken.userId);
                    tokenCleanupBatch.update(userRef, {
                        fcmToken: admin.firestore.FieldValue.delete(),
                        fcmTokenError: invalidToken.error,
                        fcmTokenRemovedAt: admin.firestore.FieldValue.serverTimestamp()
                    });
                }
                try {
                    await tokenCleanupBatch.commit();
                    console.log(`✓ Removed ${invalidTokens.length} invalid FCM tokens`);
                } catch (error) {
                    console.error(`Failed to remove invalid tokens:`, error);
                }
            }
            
            // Log users without FCM tokens (they won't receive notifications until FCM is set up)
            if (usersWithoutTokens.length > 0) {
                console.warn(`⚠ WARNING: ${usersWithoutTokens.length} users do not have FCM tokens and will NOT receive notifications:`);
                usersWithoutTokens.forEach(user => {
                    console.warn(`  - User ${user.userId} (deviceId: ${user.deviceId})`);
                });
                console.warn(`  These users need to open the app to generate FCM tokens.`);
                console.warn(`  FCM tokens are required for notifications when the app is closed.`);
            }
            
            // Mark request as processed with detailed status
            const updateData = {
                processed: true,
                sentCount: fcmSuccessCount,
                failureCount: fcmFailureCount,
                usersWithoutTokens: usersWithoutTokens.length,
                processedAt: admin.firestore.FieldValue.serverTimestamp(),
            };
            
            // Add retry information if there are failed users
            if (failedUsers.length > 0) {
                updateData.retryableFailures = failedUsers.length;
                updateData.failedUsers = failedUsers.map(u => ({
                    userId: u.userId,
                    error: u.error
                }));
                updateData.shouldRetry = true;
                updateData.retryCount = 0;
                updateData.lastRetryAttempt = null;
            }
            
            // Add detailed error information
            if (fcmFailureCount > 0) {
                updateData.errorDetails = {
                    totalFailures: fcmFailureCount,
                    invalidTokens: invalidTokens.length,
                    retryableErrors: failedUsers.length,
                    timestamp: admin.firestore.FieldValue.serverTimestamp()
                };
            }
            
            await snap.ref.update(updateData);
            
            console.log(`Request ${requestId} processed: ${fcmSuccessCount} sent via FCM, ${fcmFailureCount} failed, ${usersWithoutTokens.length} users without tokens`);
            if (failedUsers.length > 0) {
                console.log(`  → ${failedUsers.length} users marked for retry`);
            }
            if (invalidTokens.length > 0) {
                console.log(`  → ${invalidTokens.length} invalid tokens removed`);
            }
            
            return { 
                successCount: fcmSuccessCount, 
                failureCount: fcmFailureCount, 
                usersWithoutTokens: usersWithoutTokens.length,
                retryableFailures: failedUsers.length,
                invalidTokens: invalidTokens.length
            };
        } catch (error) {
            console.error(`Error processing notification request ${requestId}:`, error);
            console.error(`  Error type: ${error.constructor.name}`);
            console.error(`  Error message: ${error.message}`);
            console.error(`  Error stack: ${error.stack}`);
            
            // Mark as processed but with error - will be retried by retry function
            await snap.ref.update({
                processed: true,
                error: error.message,
                errorType: error.constructor.name,
                errorStack: error.stack,
                shouldRetry: true,
                retryCount: 0,
                processedAt: admin.firestore.FieldValue.serverTimestamp(),
            });
            
            // Don't throw - let retry function handle it
            return { successCount: 0, failureCount: 0, error: error.message };
        }
    });

/**
 * Scheduled function that runs every 5 minutes to check for events whose registration period has ended.
 * Automatically processes entrant selection for these events.
 */
exports.processAutomaticEntrantSelection = functions.pubsub
    .schedule('every 1 minutes')
    .onRun(async (context) => {
        console.log('=== Checking for events that need automatic entrant selection ===');
        const now = Date.now();
        
        try {
            // Find events where selection hasn't been processed
            // Get all events and filter in code to handle both:
            // 1. Events with selectionProcessed=false (new events with our fix)
            // 2. Events where selectionProcessed field is missing (old events before fix)
            const allEventsSnapshot = await admin.firestore()
                .collection('events')
                .get();
            
            // Filter events where selectionProcessed is false or missing
            const eventsToProcess = allEventsSnapshot.docs.filter(eventDoc => {
                const eventData = eventDoc.data();
                const selectionProcessed = eventData.selectionProcessed;
                // Treat missing field as false (needs processing)
                return selectionProcessed !== true;
            });
            
            console.log(`DEBUG: Total events: ${allEventsSnapshot.size}, Events needing processing: ${eventsToProcess.length}`);
            
            if (eventsToProcess.length === 0) {
                console.log('No events found that need selection processing');
                console.log('DEBUG: This means all events have selectionProcessed=true');
                return null;
            }
            
            console.log(`Found ${eventsToProcess.length} event(s) that may need selection processing`);
            
            let processedCount = 0;
            
            for (const eventDoc of eventsToProcess) {
                const eventId = eventDoc.id;
                const eventData = eventDoc.data();
                const registrationEnd = eventData.registrationEnd;
                const selectionProcessed = eventData.selectionProcessed;
                const selectionNotificationSent = eventData.selectionNotificationSent;
                const startsAtEpochMs = eventData.startsAtEpochMs;
                
                console.log(`\n=== Processing event ${eventId} ===`);
                console.log(`  Title: ${eventData.title || 'N/A'}`);
                console.log(`  selectionProcessed: ${selectionProcessed}`);
                console.log(`  selectionNotificationSent: ${selectionNotificationSent}`);
                console.log(`  registrationEnd: ${registrationEnd} (${registrationEnd ? new Date(registrationEnd).toISOString() : 'N/A'})`);
                console.log(`  startsAtEpochMs: ${startsAtEpochMs} (${startsAtEpochMs ? new Date(startsAtEpochMs).toISOString() : 'N/A'})`);
                console.log(`  Current time: ${new Date(now).toISOString()}`);
                
                // Skip if already processed or notification sent (double check)
                if (selectionProcessed === true || selectionNotificationSent === true) {
                    console.log(`✗ Event ${eventId} already processed (selectionProcessed=${selectionProcessed}, selectionNotificationSent=${selectionNotificationSent}), skipping`);
                    continue;
                }
                
                // Skip if registrationEnd is missing or invalid
                if (!registrationEnd || registrationEnd <= 0) {
                    console.log(`✗ Event ${eventId} has invalid registrationEnd (${registrationEnd}), skipping`);
                    continue;
                }
                
                // Skip if registration period hasn't ended yet
                if (registrationEnd > now) {
                    console.log(`✗ Event ${eventId} registration period hasn't ended yet (ends at ${new Date(registrationEnd).toISOString()}, now is ${new Date(now).toISOString()}), skipping`);
                    continue;
                }
                
                // Skip if event start date has already passed
                if (startsAtEpochMs && startsAtEpochMs > 0 && now >= startsAtEpochMs) {
                    console.log(`✗ Event ${eventId} start date has already passed (starts at ${new Date(startsAtEpochMs).toISOString()}, now is ${new Date(now).toISOString()}), skipping`);
                    continue;
                }
                
                console.log(`Processing automatic selection for event: ${eventData.title || eventId} (${eventId})`);
                console.log(`  Registration ended at: ${new Date(registrationEnd)}`);
                
                // IMPORTANT: Skip automatic selection if NonSelectedEntrants exist
                // This means initial selection already happened, and organizer should manually
                // select replacements from NonSelectedEntrants when people decline
                const nonSelectedSnapshot = await admin.firestore()
                    .collection('events').doc(eventId)
                    .collection('NonSelectedEntrants')
                    .get();
                
                if (!nonSelectedSnapshot.empty) {
                    console.log(`Event ${eventId} has ${nonSelectedSnapshot.size} NonSelectedEntrants. Skipping automatic selection - organizer must manually select replacements.`);
                    // Don't mark as processed - keep selectionProcessed=false so we don't try again
                    continue;
                }
                
                // Get waitlisted entrants
                const waitlistSnapshot = await admin.firestore()
                    .collection('events').doc(eventId)
                    .collection('WaitlistedEntrants')
                    .get();
                
                if (waitlistSnapshot.empty) {
                    console.log(`No waitlisted entrants for event ${eventId}, marking as processed`);
                    await eventDoc.ref.update({ 
                        selectionProcessed: true,
                        selectionNotificationSent: eventData.selectionNotificationSent || false,
                        sorryNotificationSent: eventData.sorryNotificationSent || false
                    });
                    continue;
                }
                
                const sampleSize = eventData.sampleSize || 0;
                if (sampleSize <= 0) {
                    console.log(`Event ${eventId} has invalid sample size: ${sampleSize}, marking as processed`);
                    await eventDoc.ref.update({ 
                        selectionProcessed: true,
                        selectionNotificationSent: eventData.selectionNotificationSent || false,
                        sorryNotificationSent: eventData.sorryNotificationSent || false
                    });
                    continue;
                }
                
                // CRITICAL FIX: First check how many are already selected to prevent race conditions
                const selectedSnapshot = await admin.firestore()
                    .collection('events').doc(eventId)
                    .collection('SelectedEntrants')
                    .get();
                
                const currentSelectedCount = selectedSnapshot.size;
                const availableSpots = sampleSize - currentSelectedCount;
                
                console.log(`Event ${eventId}: Current selected: ${currentSelectedCount}, Sample size: ${sampleSize}, Available spots: ${availableSpots}`);
                
                if (availableSpots <= 0) {
                    console.log(`Event ${eventId} already at or above sample size limit (selected: ${currentSelectedCount}, sampleSize: ${sampleSize}). Marking as processed.`);
                    await eventDoc.ref.update({ 
                        selectionProcessed: true,
                        selectionNotificationSent: eventData.selectionNotificationSent || false,
                        sorryNotificationSent: eventData.sorryNotificationSent || false
                    });
                    continue;
                }
                
                const waitlistDocs = waitlistSnapshot.docs;
                // CRITICAL: Select only availableSpots (not sampleSize!)
                const toSelect = Math.min(availableSpots, waitlistDocs.length);
                
                if (toSelect === 0) {
                    console.log(`No entrants to select for event ${eventId} (available spots: ${availableSpots}, waitlist: ${waitlistDocs.length}), marking as processed`);
                    await eventDoc.ref.update({ 
                        selectionProcessed: true,
                        selectionNotificationSent: eventData.selectionNotificationSent || false,
                        sorryNotificationSent: eventData.sorryNotificationSent || false
                    });
                    continue;
                }
                
                // Randomly select entrants using Fisher-Yates shuffle
                // This ensures truly random selection
                const shuffled = [...waitlistDocs];
                for (let i = shuffled.length - 1; i > 0; i--) {
                    const j = Math.floor(Math.random() * (i + 1));
                    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
                }
                // CRITICAL: Use toSelect (already capped at availableSpots) instead of sampleSize
                const selectedDocs = shuffled.slice(0, toSelect);
                const selectedUserIds = selectedDocs.map(doc => doc.id);
                
                // CRITICAL: Double-check that we never exceed availableSpots
                if (selectedUserIds.length > availableSpots) {
                    console.error(`CRITICAL ERROR: Selected ${selectedUserIds.length} users but availableSpots is ${availableSpots}, truncating`);
                    selectedUserIds.splice(availableSpots);
                    selectedDocs.splice(availableSpots);
                }
                
                // CRITICAL: Final enforcement - ensure we don't exceed sampleSize
                const finalCount = currentSelectedCount + selectedUserIds.length;
                if (finalCount > sampleSize) {
                    console.error(`CRITICAL: Final count (${finalCount}) would exceed sampleSize (${sampleSize})! Truncating.`);
                    const maxToAdd = sampleSize - currentSelectedCount;
                    if (maxToAdd > 0) {
                        selectedUserIds.splice(maxToAdd);
                        selectedDocs.splice(maxToAdd);
                    } else {
                        console.error(`Cannot add any more - already at sample size!`);
                        await eventDoc.ref.update({ 
                            selectionProcessed: true,
                            selectionNotificationSent: eventData.selectionNotificationSent || false,
                            sorryNotificationSent: eventData.sorryNotificationSent || false
                        });
                        continue;
                    }
                }
                
                console.log(`FINAL VERIFICATION: Will add ${selectedUserIds.length} entrants (current: ${currentSelectedCount}, new: ${selectedUserIds.length}, total: ${currentSelectedCount + selectedUserIds.length}, sampleSize: ${sampleSize})`);
                console.log(`SELECTED USER IDs: ${JSON.stringify(selectedUserIds)}`);
                
                // Move selected entrants to SelectedEntrants and remove from WaitlistedEntrants
                const batch = admin.firestore().batch();
                const selectedEntrantsRef = admin.firestore().collection('events').doc(eventId).collection('SelectedEntrants');
                const waitlistedEntrantsRef = admin.firestore().collection('events').doc(eventId).collection('WaitlistedEntrants');
                
                for (const selectedDoc of selectedDocs) {
                    const userId = selectedDoc.id;
                    const userData = selectedDoc.data();
                    
                    // CRITICAL: Ensure mutual exclusivity - user can only exist in ONE collection
                    // Add to SelectedEntrants
                    batch.set(selectedEntrantsRef.doc(userId), userData);
                    
                    // Remove from ALL other collections
                    batch.delete(waitlistedEntrantsRef.doc(userId));
                    batch.delete(admin.firestore().collection('events').doc(eventId).collection('NonSelectedEntrants').doc(userId));
                    batch.delete(admin.firestore().collection('events').doc(eventId).collection('CancelledEntrants').doc(userId));

                    const displayName = userData.fullName || userData.name || userData.firstName || '(unknown)';
                    console.log(`ENTRANT_MOVE: eventId=${eventId}, userId=${userId}, name=${displayName}, from=[Waitlisted/NonSelected/Cancelled], to=SelectedEntrants, reason=automatic_selection`);
                }
                
                // Don't update waitlistCount - users stay in waitlist
                // batch.update(eventDoc.ref, 'waitlistCount', admin.firestore.FieldValue.increment(-selectedUserIds.length));
                
                // Mark as selection processed (but NOT selectionNotificationSent yet - that happens after notification request is created)
                // Only initialize sorryNotificationSent if it doesn't exist
                batch.update(eventDoc.ref, {
                    'selectionProcessed': true,
                    'sorryNotificationSent': eventData.sorryNotificationSent || false
                    // NOTE: selectionNotificationSent will be set to true AFTER notification request is successfully created
                });
                
                await batch.commit();
                console.log(`✓ Moved ${selectedUserIds.length} entrants to SelectedEntrants for event ${eventId}`);
                
                // CRITICAL: Verify selectedUserIds only contains the users we just moved
                // Double-check by querying SelectedEntrants after batch commit
                const verifySelectedSnapshot = await admin.firestore()
                    .collection('events').doc(eventId)
                    .collection('SelectedEntrants')
                    .get();
                const actualSelectedIds = verifySelectedSnapshot.docs.map(doc => doc.id);
                console.log(`VERIFICATION: SelectedEntrants now contains ${actualSelectedIds.length} users: ${JSON.stringify(actualSelectedIds)}`);
                
                // Create invitations ONLY for selectedUserIds (the users we just moved)
                const organizerId = eventData.organizerId || 'system';
                const deadlineEpochMs = eventData.deadlineEpochMs || (now + (7 * 24 * 60 * 60 * 1000)); // Default 7 days
                const invitationBatch = admin.firestore().batch();
                
                console.log(`Creating invitations for ${selectedUserIds.length} selected users: ${JSON.stringify(selectedUserIds)}`);
                for (const userId of selectedUserIds) {
                    // CRITICAL: Verify this user is actually in SelectedEntrants before creating invitation
                    const userInSelected = actualSelectedIds.includes(userId);
                    if (!userInSelected) {
                        console.error(`⚠ WARNING: User ${userId} is NOT in SelectedEntrants but is in selectedUserIds array! Skipping invitation creation.`);
                        continue;
                    }
                    
                    const invitationId = admin.firestore().collection('invitations').doc().id;
                    const invitationRef = admin.firestore().collection('invitations').doc(invitationId);
                    
                    invitationBatch.set(invitationRef, {
                        id: invitationId,
                        eventId: eventId,
                        uid: userId,
                        entrantId: userId,
                        organizerId: organizerId,
                        status: 'PENDING',
                        issuedAt: now,
                        expiresAt: deadlineEpochMs
                    });
                    console.log(`  ✓ Creating invitation for selected user: ${userId}`);
                }
                
                await invitationBatch.commit();
                console.log(`✓ Created invitations for ${selectedUserIds.length} SELECTED users only for event ${eventId}`);
                
                // Send selection notification ONLY to selectedUserIds
                // Only create notification request if there are selected users
                if (selectedUserIds.length > 0) {
                    try {
                        const eventTitle = eventData.title || 'Event';
                        const deadlineText = deadlineEpochMs ? new Date(deadlineEpochMs).toLocaleString() : 'N/A';
                        const notificationRequest = {
                            eventId: eventId,
                            eventTitle: eventTitle,
                            organizerId: organizerId,
                            userIds: selectedUserIds, // CRITICAL: Only selected users
                            groupType: 'selection',
                            title: "You've been selected! 🎉",
                            message: `Congratulations! You've been selected for ${eventTitle}. Please check your invitations to accept or decline. Deadline to respond: ${deadlineText}`,
                            status: 'PENDING',
                            createdAt: now,
                            processed: false
                        };
                        
                        
                        console.log(`=== CREATING SELECTION NOTIFICATION REQUEST ===`);
                        console.log(`Event ID: ${eventId}`);
                        console.log(`Event Title: ${eventTitle}`);
                        console.log(`Selected User IDs: ${JSON.stringify(selectedUserIds)}`);
                        console.log(`Number of users: ${selectedUserIds.length}`);
                        console.log(`Notification title: ${notificationRequest.title}`);
                        console.log(`Notification message: ${notificationRequest.message}`);
                        
                        const notificationRequestRef = await admin.firestore().collection('notificationRequests').add(notificationRequest);
                        const notificationRequestId = notificationRequestRef.id;
                        console.log(`✓ Notification request created with ID: ${notificationRequestId}`);
                        console.log(`  Document path: notificationRequests/${notificationRequestId}`);
                        
                        // Verify the notification request was created correctly
                        const verifyRequest = await notificationRequestRef.get();
                        if (verifyRequest.exists) {
                            const verifyData = verifyRequest.data();
                            console.log(`✓ Verification: Notification request exists in Firestore`);
                            console.log(`  - userIds: ${JSON.stringify(verifyData.userIds)}`);
                            console.log(`  - groupType: ${verifyData.groupType}`);
                            console.log(`  - processed: ${verifyData.processed}`);
                            console.log(`  - eventId: ${verifyData.eventId}`);
                        } else {
                            console.error(`✗ CRITICAL ERROR: Notification request was not created!`);
                            throw new Error('Notification request verification failed');
                        }
                        
                        // Mark notification as sent
                        await eventDoc.ref.update({ selectionNotificationSent: true });
                        console.log(`✓ Marked selectionNotificationSent=true for event ${eventId}`);
                        
                        console.log(`✓ Created selection notification request for ${selectedUserIds.length} SELECTED users only for event ${eventId}`);
                        console.log(`  → This should trigger sendNotification Cloud Function automatically`);
                    } catch (error) {
                        console.error(`✗ ERROR creating selection notification request for event ${eventId}:`, error);
                        console.error(`  Error type: ${error.constructor.name}`);
                        console.error(`  Error message: ${error.message}`);
                        console.error(`  Error stack: ${error.stack}`);
                        // Still mark as sent to prevent retry loops, but log the error
                        await eventDoc.ref.update({ 
                            selectionNotificationSent: true,
                            selectionNotificationError: error.message
                        });
                        console.log(`⚠ Marked selectionNotificationSent=true despite error (to prevent retry loops)`);
                    }
                } else {
                    console.log(`⚠ No selected users for event ${eventId}, skipping notification request creation`);
                    // Still mark as sent since there's nothing to notify
                    await eventDoc.ref.update({ selectionNotificationSent: true });
                    console.log(`✓ Marked selectionNotificationSent=true (no users to notify)`);
                }
                
                // Move remaining waitlisted entrants to NonSelectedEntrants (excluding selected ones)
                // CRITICAL: These users should NOT receive invitations or selection notifications
                const remainingWaitlistSnapshot = await admin.firestore()
                    .collection('events').doc(eventId)
                    .collection('WaitlistedEntrants')
                    .get();
                
                if (!remainingWaitlistSnapshot.empty) {
                    // Filter out selected users - only move non-selected users
                    const toMove = remainingWaitlistSnapshot.docs.filter(doc => !selectedUserIds.includes(doc.id));
                    const toMoveUserIds = toMove.map(doc => doc.id);
                    
                    console.log(`Moving ${toMove.length} remaining waitlisted entrants to NonSelectedEntrants (excluding ${selectedUserIds.length} selected)`);
                    console.log(`NON-SELECTED USER IDs (should NOT get invitations): ${JSON.stringify(toMoveUserIds)}`);
                    
                    // CRITICAL: Verify these users are NOT in selectedUserIds
                    const overlap = toMoveUserIds.filter(id => selectedUserIds.includes(id));
                    if (overlap.length > 0) {
                        console.error(`⚠ CRITICAL ERROR: Found ${overlap.length} users in both selected and non-selected lists: ${JSON.stringify(overlap)}`);
                    }
                    
                    if (toMove.length > 0) {
                        const nonSelectedBatch = admin.firestore().batch();
                        const nonSelectedRef = admin.firestore().collection('events').doc(eventId).collection('NonSelectedEntrants');
                        
                        for (const remainingDoc of toMove) {
                            const userId = remainingDoc.id;
                            const userData = remainingDoc.data();
                            
                            // CRITICAL: Ensure mutual exclusivity - user can only exist in ONE collection
                            // Add to NonSelectedEntrants
                            nonSelectedBatch.set(nonSelectedRef.doc(userId), userData);
                            
                            // Remove from ALL other collections
                            nonSelectedBatch.delete(waitlistedEntrantsRef.doc(userId));
                            nonSelectedBatch.delete(admin.firestore().collection('events').doc(eventId).collection('SelectedEntrants').doc(userId));
                            nonSelectedBatch.delete(admin.firestore().collection('events').doc(eventId).collection('CancelledEntrants').doc(userId));

                            const displayName = userData.fullName || userData.name || userData.firstName || '(unknown)';
                            console.log(`ENTRANT_MOVE: eventId=${eventId}, userId=${userId}, name=${displayName}, from=[Waitlisted/Selected/Cancelled], to=NonSelectedEntrants, reason=automatic_selection_non_selected`);
                        }
                        
                        // Don't update waitlistCount - users stay in waitlist
                        // nonSelectedBatch.update(eventDoc.ref, 'waitlistCount', 0);
                        
                        await nonSelectedBatch.commit();
                        console.log(`✓ Moved ${toMove.length} NON-SELECTED entrants to NonSelectedEntrants (these users should NOT have invitations)`);
                    } else {
                        console.log('No entrants to move after filtering out selected users');
                    }
                } else {
                    console.log('No remaining waitlisted entrants to move');
                }
                
                processedCount++;
            }
            
            console.log(`✓ Processed ${processedCount} event(s) for automatic entrant selection`);
            return null;
        } catch (error) {
            console.error('Error in processAutomaticEntrantSelection:', error);
            throw error;
        }
    });

/**
 * Scheduled function that runs every minute to check for events starting in 1 minute.
 * Sends "sorry" notifications to all not selected entrants.
 */
exports.sendSorryNotificationsBeforeEventStart = functions.pubsub
    .schedule('every 1 minutes')
    .onRun(async (context) => {
        console.log('=== Checking for events starting in 1 minute ===');
        const now = Date.now();
        const oneMinuteFromNow = now + (60 * 1000); // 1 minute in milliseconds
        const twoMinutesFromNow = now + (2 * 60 * 1000); // 2 minutes in milliseconds
        
        try {
            // Find events that start between 1 and 2 minutes from now
            // (to avoid sending duplicate notifications)
            const eventsSnapshot = await admin.firestore()
                .collection('events')
                .where('startsAtEpochMs', '>=', oneMinuteFromNow)
                .where('startsAtEpochMs', '<', twoMinutesFromNow)
                .get();
            
            if (eventsSnapshot.empty) {
                console.log('No events starting in 1 minute');
                return null;
            }
            
            console.log(`Found ${eventsSnapshot.size} event(s) starting in 1 minute`);
            
            for (const eventDoc of eventsSnapshot.docs) {
                const eventId = eventDoc.id;
                const eventData = eventDoc.data();
                const eventTitle = eventData.title || 'Event';
                const startsAtEpochMs = eventData.startsAtEpochMs;
                
                // Check if sorry notification already sent
                if (eventData.sorryNotificationSent === true) {
                    console.log(`Sorry notification already sent for event ${eventId}, skipping`);
                    continue;
                }
                
                console.log(`Processing event: ${eventTitle} (${eventId})`);
                
                // Get all not selected entrants
                const nonSelectedSnapshot = await admin.firestore()
                    .collection('events').doc(eventId)
                    .collection('NonSelectedEntrants')
                    .get();
                
                if (nonSelectedSnapshot.empty) {
                    console.log(`No not selected entrants for event ${eventId}`);
                    // Mark as sent even if no entrants (to avoid checking again)
                    await eventDoc.ref.update({ sorryNotificationSent: true });
                    continue;
                }
                
                const userIds = [];
                nonSelectedSnapshot.forEach(doc => {
                    userIds.push(doc.id);
                });
                
                console.log(`Moving ${userIds.length} not selected entrants to CancelledEntrants and sending sorry notifications for event ${eventId}`);
                
                // CRITICAL: Move all not selected entrants to CancelledEntrants before sending notification
                const cancelledBatch = admin.firestore().batch();
                const cancelledRef = admin.firestore().collection('events').doc(eventId).collection('CancelledEntrants');
                const nonSelectedRef = admin.firestore().collection('events').doc(eventId).collection('NonSelectedEntrants');
                const waitlistedRef = admin.firestore().collection('events').doc(eventId).collection('WaitlistedEntrants');
                const selectedRef = admin.firestore().collection('events').doc(eventId).collection('SelectedEntrants');
                
                for (const nonSelectedDoc of nonSelectedSnapshot.docs) {
                    const userId = nonSelectedDoc.id;
                    const userData = nonSelectedDoc.data();
                    
                    // CRITICAL: Ensure mutual exclusivity - user can only exist in ONE collection
                    // Move to CancelledEntrants
                    cancelledBatch.set(cancelledRef.doc(userId), userData);
                    
                    // Remove from ALL other collections
                    cancelledBatch.delete(nonSelectedRef.doc(userId));
                    cancelledBatch.delete(waitlistedRef.doc(userId));
                    cancelledBatch.delete(selectedRef.doc(userId));

                    const displayName = userData.fullName || userData.name || userData.firstName || '(unknown)';
                    console.log(`ENTRANT_MOVE: eventId=${eventId}, userId=${userId}, name=${displayName}, from=[NonSelected/Waitlisted/Selected], to=CancelledEntrants, reason=sorry_notification_before_event_start`);
                }
                
                await cancelledBatch.commit();
                console.log(`✓ Moved ${userIds.length} not selected entrants to CancelledEntrants for event ${eventId}`);
                
                // Create notification request
                // Note: The message will be automatically personalized with "Hey [FirstName], " 
                // when processed by the sendNotification function
                const notificationRequest = {
                    eventId: eventId,
                    eventTitle: eventTitle,
                    userIds: userIds,
                    groupType: 'sorry',
                    title: `Selection Complete: ${eventTitle}`,
                    message: `Thank you for your interest in "${eventTitle}". The selection process has been completed automatically. We appreciate your participation and hope to see you at future events!`,
                    status: 'PENDING',
                    createdAt: now,
                    processed: false
                };
                
                await admin.firestore().collection('notificationRequests').add(notificationRequest);
                
                // Mark event as having sent sorry notification
                await eventDoc.ref.update({ sorryNotificationSent: true });
                
                console.log(`✓ Created notification request for ${userIds.length} not selected entrants for event ${eventId}`);
            }
            
            return null;
        } catch (error) {
            console.error('Error in sendSorryNotificationsBeforeEventStart:', error);
            throw error;
        }
    });

/**
 * Scheduled function that runs every 1 minute to retry failed notification requests.
 * Retries requests that failed due to retryable errors (network issues, temporary failures).
 */
exports.retryFailedNotifications = functions.pubsub
    .schedule('every 1 minutes')
    .onRun(async (context) => {
        console.log('=== Checking for failed notification requests to retry ===');
        const now = Date.now();
        const maxRetries = 3; // Maximum number of retry attempts
        const retryDelayMs = 60 * 1000; // 1 minute between retries
        
        try {
            // Find notification requests that should be retried
            // Only retry requests that:
            // 1. Are marked as processed (so they were attempted)
            // 2. Have shouldRetry = true
            // 3. Have retryCount < maxRetries
            // 4. Either have no lastRetryAttempt or lastRetryAttempt was more than retryDelayMs ago
            const requestsSnapshot = await admin.firestore()
                .collection('notificationRequests')
                .where('processed', '==', true)
                .where('shouldRetry', '==', true)
                .get();
            
            if (requestsSnapshot.empty) {
                console.log('No failed notification requests found to retry');
                return null;
            }
            
            console.log(`Found ${requestsSnapshot.size} notification request(s) that may need retry`);
            
            let retriedCount = 0;
            let skippedCount = 0;
            
            for (const requestDoc of requestsSnapshot.docs) {
                const requestId = requestDoc.id;
                const requestData = requestDoc.data();
                
                // Check retry count
                const retryCount = requestData.retryCount || 0;
                if (retryCount >= maxRetries) {
                    console.log(`Request ${requestId} has exceeded max retries (${retryCount}/${maxRetries}), skipping`);
                    // Mark as no longer retryable
                    await requestDoc.ref.update({
                        shouldRetry: false,
                        retryExceeded: true,
                        finalStatus: 'failed'
                    });
                    skippedCount++;
                    continue;
                }
                
                // Check if enough time has passed since last retry
                const lastRetryAttempt = requestData.lastRetryAttempt;
                if (lastRetryAttempt) {
                    const timeSinceLastRetry = now - lastRetryAttempt.toMillis();
                    if (timeSinceLastRetry < retryDelayMs) {
                        console.log(`Request ${requestId} retried recently (${Math.floor(timeSinceLastRetry / 1000)}s ago), skipping`);
                        skippedCount++;
                        continue;
                    }
                }
                
                // Check if request has failed users to retry
                const failedUsers = requestData.failedUsers || [];
                if (failedUsers.length === 0 && !requestData.error) {
                    console.log(`Request ${requestId} has no failed users to retry, marking as no longer retryable`);
                    await requestDoc.ref.update({
                        shouldRetry: false
                    });
                    skippedCount++;
                    continue;
                }
                
                console.log(`Retrying notification request ${requestId} (attempt ${retryCount + 1}/${maxRetries})`);
                
                try {
                    // Re-fetch user data for failed users
                    const userIdsToRetry = failedUsers.map(u => u.userId);
                    if (userIdsToRetry.length === 0 && requestData.userIds) {
                        // If no specific failed users, retry all original users
                        userIdsToRetry.push(...requestData.userIds);
                    }
                    
                    if (userIdsToRetry.length === 0) {
                        console.log(`Request ${requestId} has no users to retry, marking as no longer retryable`);
                        await requestDoc.ref.update({
                            shouldRetry: false
                        });
                        skippedCount++;
                        continue;
                    }
                    
                    // Fetch current FCM tokens for users
                    const userDocs = await Promise.all(
                        userIdsToRetry.map(userId => 
                            admin.firestore().collection('users').doc(userId).get()
                        )
                    );
                    
                    const retryMessages = [];
                    const retryUserNotifications = [];
                    
                    userDocs.forEach((doc, index) => {
                        if (doc.exists) {
                            const userData = doc.data();
                            const fcmToken = userData.fcmToken;
                            const notificationsEnabled = userData.notificationsEnabled !== false; // Default to true
                            
                            // CRITICAL: Also check specific notification preferences based on groupType
                            let preferenceEnabled = true; // Default to enabled
                            const retryGroupType = requestData.groupType || 'general';
                            if (retryGroupType === 'selected' || retryGroupType === 'selection') {
                                // For selected/invited notifications, check notificationPreferenceInvited
                                const invitedPref = userData.notificationPreferenceInvited;
                                if (invitedPref !== undefined && invitedPref !== null) {
                                    preferenceEnabled = Boolean(invitedPref);
                                }
                            } else if (retryGroupType === 'nonSelected' || retryGroupType === 'sorry') {
                                // For not-invited/sorry notifications, check notificationPreferenceNotInvited
                                const notInvitedPref = userData.notificationPreferenceNotInvited;
                                if (notInvitedPref !== undefined && notInvitedPref !== null) {
                                    preferenceEnabled = Boolean(notInvitedPref);
                                }
                            }
                            
                            // User must have both notificationsEnabled AND the specific preference enabled
                            if (fcmToken && notificationsEnabled && preferenceEnabled) {
                                // Get first name for personalization
                                let firstName = null;
                                if (userData.firstName && userData.firstName.trim()) {
                                    firstName = userData.firstName.trim();
                                } else if (userData.fullName && userData.fullName.trim()) {
                                    firstName = userData.fullName.trim().split(' ')[0];
                                } else if (userData.name && userData.name.trim()) {
                                    firstName = userData.name.trim().split(' ')[0];
                                }
                                
                                const capitalizedName = firstName ? 
                                    firstName.charAt(0).toUpperCase() + firstName.slice(1).toLowerCase() : null;
                                const personalizedMessage = capitalizedName ? 
                                    `Hey ${capitalizedName}, ${requestData.message || 'You have an update regarding an event.'}` :
                                    requestData.message || 'You have an update regarding an event.';
                                
                                retryUserNotifications.push({
                                    token: fcmToken,
                                    userId: userIdsToRetry[index],
                                    firstName: firstName
                                });
                                
                                retryMessages.push({
                                    token: fcmToken,
                                    notification: {
                                        title: requestData.title || 'Event Update',
                                        body: personalizedMessage,
                                    },
                                    data: {
                                        type: retryGroupType,
                                        eventId: requestData.eventId || '',
                                        eventTitle: requestData.eventTitle || 'Event',
                                        title: requestData.title || 'Event Update',
                                        message: personalizedMessage,
                                        click_action: 'FLUTTER_NOTIFICATION_CLICK',
                                    },
                                    android: {
                                        priority: 'high',
                                        notification: {
                                            channelId: 'event_invitations',
                                            sound: 'default',
                                            priority: 'high',
                                        },
                                    },
                                });
                            }
                        }
                    });
                    
                    if (retryMessages.length === 0) {
                        console.log(`Request ${requestId} has no valid tokens to retry, marking as no longer retryable`);
                        await requestDoc.ref.update({
                            shouldRetry: false,
                            retryError: 'No valid FCM tokens found'
                        });
                        skippedCount++;
                        continue;
                    }
                    
                    // Send retry notifications
                    const batchSize = 500;
                    let retrySuccessCount = 0;
                    let retryFailureCount = 0;
                    
                    for (let i = 0; i < retryMessages.length; i += batchSize) {
                        const batch = retryMessages.slice(i, i + batchSize);
                        try {
                            const response = await admin.messaging().sendEach(batch);
                            retrySuccessCount += response.successCount;
                            retryFailureCount += response.failureCount;
                            
                            console.log(`Retry batch ${Math.floor(i / batchSize) + 1}: ${response.successCount} sent, ${response.failureCount} failed`);
                        } catch (error) {
                            console.error(`Error sending retry batch:`, error);
                            retryFailureCount += batch.length;
                        }
                    }
                    
                    // Update request with retry results
                    const newRetryCount = retryCount + 1;
                    const updateData = {
                        lastRetryAttempt: admin.firestore.FieldValue.serverTimestamp(),
                        retryCount: newRetryCount,
                        retrySuccessCount: (requestData.retrySuccessCount || 0) + retrySuccessCount,
                        retryFailureCount: (requestData.retryFailureCount || 0) + retryFailureCount,
                    };
                    
                    // If all retries succeeded or max retries reached, mark as no longer retryable
                    if (retryFailureCount === 0 || newRetryCount >= maxRetries) {
                        updateData.shouldRetry = false;
                        if (retryFailureCount === 0) {
                            updateData.finalStatus = 'success';
                        } else {
                            updateData.finalStatus = 'failed';
                        }
                    }
                    
                    await requestDoc.ref.update(updateData);
                    
                    console.log(`✓ Retry attempt ${newRetryCount} for request ${requestId}: ${retrySuccessCount} sent, ${retryFailureCount} failed`);
                    retriedCount++;
                    
                } catch (error) {
                    console.error(`Error retrying notification request ${requestId}:`, error);
                    // Increment retry count even on error
                    await requestDoc.ref.update({
                        retryCount: retryCount + 1,
                        lastRetryAttempt: admin.firestore.FieldValue.serverTimestamp(),
                        retryError: error.message
                    });
                }
            }
            
            console.log(`✓ Retry process complete: ${retriedCount} retried, ${skippedCount} skipped`);
            return null;
        } catch (error) {
            console.error('Error in retryFailedNotifications:', error);
            throw error;
        }
    });


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
        
        // Skip if already processed
        if (requestData.processed === true) {
            console.log(`Request ${requestId} already processed, skipping`);
            return null;
        }
        
        const { userIds, title, message, eventId, eventTitle, groupType } = requestData;
        
        if (!userIds || !Array.isArray(userIds) || userIds.length === 0) {
            console.log(`Request ${requestId} has no userIds, marking as processed`);
            await snap.ref.update({ processed: true, error: 'No userIds provided' });
            return null;
        }
        
            console.log(`=== Processing notification request ${requestId} ===`);
            console.log(`Title: ${title}`);
            console.log(`Message: ${message}`);
            console.log(`Event ID: ${eventId}`);
            console.log(`Group Type: ${groupType}`);
            console.log(`User IDs: ${JSON.stringify(userIds)}`);
            console.log(`Processing for ${userIds.length} users`);
        
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
                    
                    console.log(`User ${userIds[index]}:`);
                    console.log(`  - Token exists: ${!!fcmToken}`);
                    console.log(`  - Token length: ${fcmToken ? fcmToken.length : 0}`);
                    console.log(`  - notificationsEnabled (raw): ${notificationsEnabledRaw} (type: ${typeof notificationsEnabledRaw})`);
                    console.log(`  - notificationsEnabled (parsed): ${notificationsEnabled}`);
                    
                    if (fcmToken && notificationsEnabled) {
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
                        const reason = !fcmToken ? 'no token' : 'notifications disabled in Firestore';
                        console.log(`✗ User ${userIds[index]}: ${reason}`);
                    }
                } else {
                    console.log(`✗ User ${userIds[index]} not found in Firestore`);
                }
            });
            
            if (userNotifications.length === 0) {
                console.log(`No valid FCM tokens found for request ${requestId}`);
                await snap.ref.update({ 
                    processed: true, 
                    error: 'No valid FCM tokens found',
                    sentCount: 0
                });
                return null;
            }
            
            // Send personalized notifications
            // Use sendEach for individual messages (allows personalization per user)
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
            
            // Send notifications in batches (FCM allows up to 500 messages per batch)
            const batchSize = 500;
            let successCount = 0;
            let failureCount = 0;
            
            console.log(`Sending personalized notifications to ${userNotifications.length} users`);
            for (let i = 0; i < messages.length; i += batchSize) {
                const batch = messages.slice(i, i + batchSize);
                
                try {
                    console.log(`Sending batch ${Math.floor(i / batchSize) + 1} to ${batch.length} users`);
                    const response = await admin.messaging().sendEach(batch);
                    
                    console.log(`Batch ${Math.floor(i / batchSize) + 1} result: ${response.successCount} sent, ${response.failureCount} failed`);
                    
                    successCount += response.successCount;
                    failureCount += response.failureCount;
                    
                    // Log failures
                    if (response.failureCount > 0) {
                        response.responses.forEach((resp, idx) => {
                            if (!resp.success) {
                                const userId = userNotifications[i + idx].userId;
                                console.error(`✗ Failed to send to user ${userId}:`, resp.error);
                                if (resp.error) {
                                    console.error(`  Error code: ${resp.error.code}, Error message: ${resp.error.message}`);
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
                    failureCount += batch.length;
                }
            }
            
            // Mark request as processed
            await snap.ref.update({
                processed: true,
                sentCount: successCount,
                failureCount: failureCount,
                processedAt: admin.firestore.FieldValue.serverTimestamp(),
            });
            
            console.log(`Request ${requestId} processed: ${successCount} sent, ${failureCount} failed`);
            
            return { successCount, failureCount };
        } catch (error) {
            console.error(`Error processing notification request ${requestId}:`, error);
            await snap.ref.update({
                processed: true,
                error: error.message,
                processedAt: admin.firestore.FieldValue.serverTimestamp(),
            });
            throw error;
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
            // Query only by selectionProcessed to avoid needing a composite index
            // Then filter by registrationEnd in code
            const eventsSnapshot = await admin.firestore()
                .collection('events')
                .where('selectionProcessed', '==', false)
                .get();
            
            if (eventsSnapshot.empty) {
                console.log('No events found that need selection processing');
                return null;
            }
            
            console.log(`Found ${eventsSnapshot.size} event(s) that may need selection processing`);
            
            let processedCount = 0;
            
            for (const eventDoc of eventsSnapshot.docs) {
                const eventId = eventDoc.id;
                const eventData = eventDoc.data();
                const registrationEnd = eventData.registrationEnd;
                const selectionProcessed = eventData.selectionProcessed;
                const selectionNotificationSent = eventData.selectionNotificationSent;
                const startsAtEpochMs = eventData.startsAtEpochMs;
                
                // Skip if already processed or notification sent (double check)
                if (selectionProcessed === true || selectionNotificationSent === true) {
                    console.log(`Event ${eventId} already processed, skipping`);
                    continue;
                }
                
                // Skip if registrationEnd is missing or invalid
                if (!registrationEnd || registrationEnd <= 0) {
                    console.log(`Event ${eventId} has invalid registrationEnd, skipping`);
                    continue;
                }
                
                // Skip if registration period hasn't ended yet
                if (registrationEnd > now) {
                    console.log(`Event ${eventId} registration period hasn't ended yet (ends at ${new Date(registrationEnd)})`);
                    continue;
                }
                
                // Skip if event start date has already passed
                if (startsAtEpochMs && startsAtEpochMs > 0 && now >= startsAtEpochMs) {
                    console.log(`Event ${eventId} start date has already passed, skipping`);
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
                    await eventDoc.ref.update({ selectionProcessed: true });
                    continue;
                }
                
                const sampleSize = eventData.sampleSize || 0;
                if (sampleSize <= 0) {
                    console.log(`Event ${eventId} has invalid sample size: ${sampleSize}, marking as processed`);
                    await eventDoc.ref.update({ selectionProcessed: true });
                    continue;
                }
                
                const waitlistDocs = waitlistSnapshot.docs;
                const toSelect = Math.min(sampleSize, waitlistDocs.length);
                
                if (toSelect === 0) {
                    console.log(`No entrants to select for event ${eventId}, marking as processed`);
                    await eventDoc.ref.update({ selectionProcessed: true });
                    continue;
                }
                
                // Randomly select entrants using Fisher-Yates shuffle
                // This ensures truly random selection
                const shuffled = [...waitlistDocs];
                for (let i = shuffled.length - 1; i > 0; i--) {
                    const j = Math.floor(Math.random() * (i + 1));
                    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
                }
                const selectedDocs = shuffled.slice(0, toSelect);
                const selectedUserIds = selectedDocs.map(doc => doc.id);
                
                // CRITICAL: Ensure we never select more than sampleSize
                if (selectedUserIds.length > sampleSize) {
                    console.error(`ERROR: Selected ${selectedUserIds.length} users but sampleSize is ${sampleSize}, truncating`);
                    selectedUserIds.splice(sampleSize);
                    selectedDocs.splice(sampleSize);
                }
                
                console.log(`Selected ${selectedUserIds.length} out of ${waitlistDocs.length} waitlisted entrants for event ${eventId}`);
                
                // Move selected entrants to SelectedEntrants and remove from WaitlistedEntrants
                const batch = admin.firestore().batch();
                const selectedEntrantsRef = admin.firestore().collection('events').doc(eventId).collection('SelectedEntrants');
                const waitlistedEntrantsRef = admin.firestore().collection('events').doc(eventId).collection('WaitlistedEntrants');
                
                for (const selectedDoc of selectedDocs) {
                    const userId = selectedDoc.id;
                    const userData = selectedDoc.data();
                    
                    // Add to SelectedEntrants
                    batch.set(selectedEntrantsRef.doc(userId), userData);
                    
                    // Keep users in WaitlistedEntrants (don't delete)
                    // batch.delete(waitlistedEntrantsRef.doc(userId));
                }
                
                // Don't update waitlistCount - users stay in waitlist
                // batch.update(eventDoc.ref, 'waitlistCount', admin.firestore.FieldValue.increment(-selectedUserIds.length));
                
                // Mark as selection processed
                batch.update(eventDoc.ref, 'selectionProcessed', true);
                
                await batch.commit();
                console.log(`✓ Moved ${selectedUserIds.length} entrants to SelectedEntrants for event ${eventId}`);
                
                // Create invitations for selected entrants
                const organizerId = eventData.organizerId || 'system';
                const deadlineEpochMs = eventData.deadlineEpochMs || (now + (7 * 24 * 60 * 60 * 1000)); // Default 7 days
                const invitationBatch = admin.firestore().batch();
                
                for (const userId of selectedUserIds) {
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
                }
                
                await invitationBatch.commit();
                console.log(`✓ Created ${selectedUserIds.length} invitations for event ${eventId}`);
                
                // Send selection notification
                const eventTitle = eventData.title || 'Event';
                const deadlineText = deadlineEpochMs ? new Date(deadlineEpochMs).toLocaleString() : 'N/A';
                const notificationRequest = {
                    eventId: eventId,
                    eventTitle: eventTitle,
                    organizerId: organizerId,
                    userIds: selectedUserIds,
                    groupType: 'selection',
                    title: "You've been selected! 🎉",
                    message: `Congratulations! You've been selected for ${eventTitle}. Please check your invitations to accept or decline. Deadline to respond: ${deadlineText}`,
                    status: 'PENDING',
                    createdAt: now,
                    processed: false
                };
                
                await admin.firestore().collection('notificationRequests').add(notificationRequest);
                await eventDoc.ref.update({ selectionNotificationSent: true });
                
                console.log(`✓ Created selection notification request for ${selectedUserIds.length} users for event ${eventId}`);
                
                // Move remaining waitlisted entrants to NonSelectedEntrants (excluding selected ones)
                const remainingWaitlistSnapshot = await admin.firestore()
                    .collection('events').doc(eventId)
                    .collection('WaitlistedEntrants')
                    .get();
                
                if (!remainingWaitlistSnapshot.empty) {
                    // Filter out selected users - only move non-selected users
                    const toMove = remainingWaitlistSnapshot.docs.filter(doc => !selectedUserIds.includes(doc.id));
                    
                    console.log(`Moving ${toMove.length} remaining waitlisted entrants to NonSelectedEntrants (excluding ${selectedUserIds.length} selected)`);
                    
                    if (toMove.length > 0) {
                        const nonSelectedBatch = admin.firestore().batch();
                        const nonSelectedRef = admin.firestore().collection('events').doc(eventId).collection('NonSelectedEntrants');
                        
                        for (const remainingDoc of toMove) {
                            const userId = remainingDoc.id;
                            const userData = remainingDoc.data();
                            
                            // Add to NonSelectedEntrants
                            nonSelectedBatch.set(nonSelectedRef.doc(userId), userData);
                            
                            // Keep users in WaitlistedEntrants (don't delete)
                            // nonSelectedBatch.delete(remainingWaitlistRef.doc(userId));
                        }
                        
                        // Don't update waitlistCount - users stay in waitlist
                        // nonSelectedBatch.update(eventDoc.ref, 'waitlistCount', 0);
                        
                        await nonSelectedBatch.commit();
                        console.log(`✓ Moved ${toMove.length} remaining entrants to NonSelectedEntrants`);
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
                
                console.log(`Sending sorry notifications to ${userIds.length} not selected entrants for event ${eventId}`);
                
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


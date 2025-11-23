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


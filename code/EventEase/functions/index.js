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
        
        console.log(`Processing notification request ${requestId} for ${userIds.length} users`);
        
        try {
            // Fetch FCM tokens for all users
            const userDocs = await Promise.all(
                userIds.map(userId => 
                    admin.firestore().collection('users').document(userId).get()
                )
            );
            
            const tokens = [];
            const validUserIds = [];
            
            userDocs.forEach((doc, index) => {
                if (doc.exists) {
                    const fcmToken = doc.data().fcmToken;
                    if (fcmToken) {
                        tokens.push(fcmToken);
                        validUserIds.push(userIds[index]);
                    } else {
                        console.log(`User ${userIds[index]} has no FCM token`);
                    }
                } else {
                    console.log(`User ${userIds[index]} not found`);
                }
            });
            
            if (tokens.length === 0) {
                console.log(`No valid FCM tokens found for request ${requestId}`);
                await snap.ref.update({ 
                    processed: true, 
                    error: 'No valid FCM tokens found',
                    sentCount: 0
                });
                return null;
            }
            
            // Prepare notification payload
            const notificationPayload = {
                notification: {
                    title: title || 'Event Update',
                    body: message || 'You have an update regarding an event.',
                },
                data: {
                    type: groupType || 'general',
                    eventId: eventId || '',
                    eventTitle: eventTitle || 'Event',
                    click_action: 'FLUTTER_NOTIFICATION_CLICK', // For Android
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
            
            // Send notifications in batches (FCM allows up to 500 tokens per batch)
            const batchSize = 500;
            let successCount = 0;
            let failureCount = 0;
            
            for (let i = 0; i < tokens.length; i += batchSize) {
                const batch = tokens.slice(i, i + batchSize);
                
                try {
                    const response = await admin.messaging().sendEachForMulticast({
                        tokens: batch,
                        ...notificationPayload,
                    });
                    
                    successCount += response.successCount;
                    failureCount += response.failureCount;
                    
                    // Log failures
                    if (response.failureCount > 0) {
                        response.responses.forEach((resp, idx) => {
                            if (!resp.success) {
                                console.error(`Failed to send to token ${batch[idx]}:`, resp.error);
                            }
                        });
                    }
                } catch (error) {
                    console.error(`Error sending batch ${i / batchSize + 1}:`, error);
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


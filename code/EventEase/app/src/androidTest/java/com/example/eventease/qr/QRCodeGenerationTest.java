package com.example.eventease.qr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Test case to generate a QR code for an event and save it locally
 * so it can be checked on a laptop.
 * 
 * To retrieve the QR code from device:
 * 1. Run this test: ./gradlew connectedAndroidTest
 * 2. Pull the file: adb pull /sdcard/Download/EventEase_Test_QR.png ~/Desktop/
 */
@RunWith(AndroidJUnit4.class)
public class QRCodeGenerationTest {
    private static final String TAG = "QRCodeGenerationTest";

    @Test
    public void testGenerateAndSaveQRCode() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        
        // Create a test event ID
        String eventId = UUID.randomUUID().toString();
        String qrPayload = "eventease://event/" + eventId;
        
        Log.d(TAG, "Authenticating for Firestore access...");
        
        // Authenticate anonymously to get Firestore write permissions
        authenticateForTest();
        
        Log.d(TAG, "Creating test event in Firestore: " + eventId);
        
        // Create a complete event document in Firestore
        createTestEventInFirestore(eventId, qrPayload);
        
        Log.d(TAG, "Generating QR code for event: " + eventId);
        Log.d(TAG, "QR Payload: " + qrPayload);
        
        // Generate QR code bitmap
        Bitmap qrBitmap = generateQrBitmap(qrPayload);
        
        assertNotNull("QR code bitmap should not be null", qrBitmap);
        assertTrue("QR code bitmap should have valid dimensions", 
                   qrBitmap.getWidth() > 0 && qrBitmap.getHeight() > 0);
        
        Log.d(TAG, "QR code generated successfully. Size: " + 
                   qrBitmap.getWidth() + "x" + qrBitmap.getHeight());
        
        // Save to Downloads folder
        File savedFile = saveQrToDownloads(context, qrBitmap, eventId);
        
        assertNotNull("QR code file should be saved", savedFile);
        assertTrue("QR code file should exist", savedFile.exists());
        assertTrue("QR code file should be readable", savedFile.canRead());
        
        Log.d(TAG, "QR code saved successfully to: " + savedFile.getAbsolutePath());
        Log.d(TAG, "File size: " + savedFile.length() + " bytes");
        
        // Print instructions
        System.out.println("\n==========================================");
        System.out.println("QR Code Test - SUCCESS");
        System.out.println("==========================================");
        System.out.println("Event ID: " + eventId);
        System.out.println("Event Title: Test Event - QR Code Test");
        System.out.println("QR Payload: " + qrPayload);
        System.out.println("File saved to: " + savedFile.getAbsolutePath());
        System.out.println("\n✅ Event created in Firestore with all details");
        System.out.println("✅ QR code generated and saved");
        System.out.println("\nTo test the deep link:");
        System.out.println("  adb shell am start -a android.intent.action.VIEW -d \"" + qrPayload + "\" com.example.eventease");
        System.out.println("\nTo retrieve the QR code:");
        System.out.println("  adb pull " + savedFile.getAbsolutePath() + " ~/Desktop/");
        System.out.println("\nOr try these paths:");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            System.out.println("  Android 10+: /sdcard/Pictures/EventEase/" + savedFile.getName());
            System.out.println("  adb pull /sdcard/Pictures/EventEase/" + savedFile.getName() + " ~/Desktop/");
        } else {
            System.out.println("  Android 9-: /sdcard/Download/EventEase/" + savedFile.getName());
            System.out.println("  adb pull /sdcard/Download/EventEase/" + savedFile.getName() + " ~/Desktop/");
        }
        System.out.println("==========================================\n");
    }

    /**
     * Authenticates anonymously to get Firestore write permissions.
     * Required because Firestore security rules require authentication.
     */
    private void authenticateForTest() throws Exception {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        
        // Sign out first to ensure clean state (signOut() returns void, not a Task)
        if (auth.getCurrentUser() != null) {
            auth.signOut();
            // Wait a bit for sign out to complete
            Thread.sleep(500);
        }
        
        // Sign in anonymously
        Tasks.await(auth.signInAnonymously());
        
        Log.d(TAG, "Authenticated anonymously. UID: " + 
                   (auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "null"));
    }

    /**
     * Creates a complete test event in Firestore with all required fields.
     * This ensures the deep link will work when tested.
     */
    private void createTestEventInFirestore(String eventId, String qrPayload) throws Exception {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Calculate dates (registration starts now, ends in 7 days, deadline in 14 days)
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        
        long registrationStart = now;
        cal.add(Calendar.DAY_OF_MONTH, 7);
        long registrationEnd = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_MONTH, 7);
        long deadlineEpochMs = cal.getTimeInMillis();
        
        // Create event document with all required fields
        Map<String, Object> eventDoc = new HashMap<>();
        eventDoc.put("id", eventId);
        eventDoc.put("title", "Test Event - QR Code Test");
        eventDoc.put("description", "This is a test event created for QR code testing. It includes all required fields like title, description, location, registration period, and capacity.");
        eventDoc.put("notes", "This is a test event created for QR code testing. It includes all required fields like title, description, location, registration period, and capacity.");
        eventDoc.put("guidelines", "Test event guidelines: Please arrive on time. Bring your ticket. Have fun!");
        eventDoc.put("location", "Test Location, Test City");
        eventDoc.put("registrationStart", registrationStart);
        eventDoc.put("registrationEnd", registrationEnd);
        eventDoc.put("deadlineEpochMs", deadlineEpochMs);
        eventDoc.put("startsAtEpochMs", deadlineEpochMs); // Event starts at deadline
        eventDoc.put("capacity", 50);
        eventDoc.put("waitlistCount", 0);
        eventDoc.put("waitlist", new ArrayList<String>());
        eventDoc.put("admitted", new ArrayList<String>());
        eventDoc.put("geolocation", false);
        eventDoc.put("qrEnabled", true);
        eventDoc.put("qrPayload", qrPayload);
        eventDoc.put("posterUrl", null); // No poster for test event
        eventDoc.put("organizerId", "test-organizer-id"); // Test organizer ID
        eventDoc.put("createdAt", now);
        eventDoc.put("createdAtEpochMs", now);
        
        // Write to Firestore
        Tasks.await(db.collection("events").document(eventId).set(eventDoc));
        
        Log.d(TAG, "Test event created in Firestore: " + eventId);
        Log.d(TAG, "Event title: Test Event - QR Code Test");
        Log.d(TAG, "Registration period: " + new java.util.Date(registrationStart) + " to " + new java.util.Date(registrationEnd));
        Log.d(TAG, "Event deadline: " + new java.util.Date(deadlineEpochMs));
    }

    /**
     * Generates a QR code bitmap from the given payload.
     * Uses the same implementation as OrganizerCreateEventActivity.
     */
    private Bitmap generateQrBitmap(String payload) {
        try {
            // Validate payload
            if (payload == null || payload.trim().isEmpty()) {
                Log.e(TAG, "QR payload is null or empty");
                return null;
            }
            
            QRCodeWriter writer = new QRCodeWriter();
            
            // Set encoding hints for better QR code quality and error correction
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H); // High error correction
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 2); // Add margin for better scanning
            
            // Generate QR code with error correction
            BitMatrix matrix = writer.encode(payload, BarcodeFormat.QR_CODE, 512, 512, hints);
            
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            
            // Fill bitmap with QR code pattern
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            
            Log.d(TAG, "QR code generated successfully. Payload: " + payload + ", Size: " + width + "x" + height);
            return bmp;
        } catch (WriterException e) {
            Log.e(TAG, "QR code generation failed", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error generating QR code", e);
            return null;
        }
    }

    /**
     * Saves the QR code bitmap to the Pictures folder (Android 10+ requires Pictures or DCIM).
     * For older versions, saves to Downloads folder.
     * Works on Android 10+ (API 29+) using MediaStore, and older versions using direct file access.
     */
    private File saveQrToDownloads(Context context, Bitmap bitmap, String eventId) {
        String fileName = "EventEase_Test_QR_" + eventId.substring(0, 8) + ".png";
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+): Use MediaStore - must use Pictures or DCIM folder
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png");
                // Use Pictures folder (allowed) with EventEase subfolder
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, 
                          Environment.DIRECTORY_PICTURES + "/EventEase");
                
                android.net.Uri uri = context.getContentResolver().insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                
                if (uri == null) {
                    Log.e(TAG, "Unable to create MediaStore entry, falling back to direct file");
                    return saveQrToFileDirect(context, bitmap, fileName);
                }
                
                try (java.io.OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                    if (out == null) {
                        Log.e(TAG, "Unable to open output stream, falling back to direct file");
                        return saveQrToFileDirect(context, bitmap, fileName);
                    }
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    Log.d(TAG, "QR code saved via MediaStore: " + uri);
                    
                    // Try to get the file path for the return value
                    String[] projection = {android.provider.MediaStore.Images.Media.DATA};
                    android.database.Cursor cursor = context.getContentResolver().query(
                        uri, projection, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA);
                        String filePath = cursor.getString(columnIndex);
                        cursor.close();
                        return new File(filePath);
                    }
                    if (cursor != null) cursor.close();
                    // If we can't get the path, return a placeholder
                    return new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "EventEase/" + fileName);
                }
            } else {
                // Android 9 and below: Direct file access to Downloads
                return saveQrToFileDirect(context, bitmap, fileName);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to save QR code", e);
            return null;
        }
    }

    /**
     * Saves QR code directly to file (for Android 9 and below, or as fallback).
     * Uses Downloads folder for older Android versions.
     */
    private File saveQrToFileDirect(Context context, Bitmap bitmap, String fileName) throws IOException {
        // For Android 9 and below, use Downloads
        // For Android 10+, this is fallback, so use Pictures
        String directory = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q 
            ? Environment.DIRECTORY_PICTURES 
            : Environment.DIRECTORY_DOWNLOADS;
        
        File baseDir = Environment.getExternalStoragePublicDirectory(directory);
        File eventEaseDir = new File(baseDir, "EventEase");
        
        if (!eventEaseDir.exists() && !eventEaseDir.mkdirs()) {
            Log.w(TAG, "Could not create EventEase directory, using base directory directly");
            eventEaseDir = baseDir;
        }
        
        File file = new File(eventEaseDir, fileName);
        
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            Log.d(TAG, "QR code saved directly to: " + file.getAbsolutePath());
        }
        
        return file;
    }
}


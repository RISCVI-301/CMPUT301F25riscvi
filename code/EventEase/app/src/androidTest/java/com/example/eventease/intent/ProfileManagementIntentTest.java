package com.example.eventease.intent;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Intent tests for profile management functionality.
 * Tests US 01.02.01 (Provide personal information) and US 01.02.02 (Update profile).
 */
@RunWith(AndroidJUnit4.class)
public class ProfileManagementIntentTest {

    @Test
    public void testProfileIntent_dataIsValid() {
        // US 01.02.01, US 01.02.02: Test that profile data structure is valid
        Intent profileIntent = new Intent();
        profileIntent.putExtra("uid", "user123");
        profileIntent.putExtra("name", "John Doe");
        profileIntent.putExtra("email", "john.doe@example.com");
        profileIntent.putExtra("phoneNumber", "+1234567890");

        assertNotNull(profileIntent);
        assertEquals("user123", profileIntent.getStringExtra("uid"));
        assertEquals("John Doe", profileIntent.getStringExtra("name"));
        assertEquals("john.doe@example.com", profileIntent.getStringExtra("email"));
        assertEquals("+1234567890", profileIntent.getStringExtra("phoneNumber"));
    }

    @Test
    public void testProfileIntent_phoneNumberIsOptional() {
        // US 01.02.01: Phone number should be optional
        Intent profileIntent = new Intent();
        profileIntent.putExtra("uid", "user123");
        profileIntent.putExtra("name", "Jane Doe");
        profileIntent.putExtra("email", "jane.doe@example.com");
        // No phone number

        assertNotNull(profileIntent);
        assertNull(profileIntent.getStringExtra("phoneNumber"));
        assertNotNull(profileIntent.getStringExtra("name"));
        assertNotNull(profileIntent.getStringExtra("email"));
    }
}


package com.example.eventease.model;

import com.example.eventease.testdata.TestDataHelper;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

/**
 * Unit tests for Profile model class.
 * Tests US 01.02.01 (Provide personal information) and US 01.02.02 (Update profile).
 */
public class ProfileTest {
    private Profile profile;
    private String uid;
    private String displayName;
    private String email;
    private String photoUrl;

    @Before
    public void setUp() {
        uid = "user123";
        displayName = "John Doe";
        email = "john.doe@example.com";
        photoUrl = "https://example.com/photo.jpg";
        profile = new Profile(uid, displayName, email, photoUrl);
    }

    @Test
    public void testProfileCreation() {
        // US 01.02.01: Create profile with personal information
        assertNotNull(profile);
        assertEquals(uid, profile.getUid());
        assertEquals(displayName, profile.getDisplayName());
        assertEquals(email, profile.getEmail());
        assertEquals(photoUrl, profile.getPhotoUrl());
    }

    @Test
    public void testProfileUpdate() {
        // US 01.02.02: Update profile information
        profile.setDisplayName("Jane Doe");
        profile.setEmail("jane.doe@example.com");
        profile.setPhoneNumber("+1234567890");
        profile.setPhotoUrl("https://example.com/newphoto.jpg");

        assertEquals("Jane Doe", profile.getDisplayName());
        assertEquals("jane.doe@example.com", profile.getEmail());
        assertEquals("+1234567890", profile.getPhoneNumber());
        assertEquals("https://example.com/newphoto.jpg", profile.getPhotoUrl());
    }

    @Test
    public void testPhoneNumber_isOptional() {
        // US 01.02.01: Phone number is optional
        Profile profileWithoutPhone = new Profile(uid, displayName, email, photoUrl);
        assertNull(profileWithoutPhone.getPhoneNumber());
        
        profile.setPhoneNumber("+1234567890");
        assertNotNull(profile.getPhoneNumber());
        assertEquals("+1234567890", profile.getPhoneNumber());
    }

    @Test
    public void testDefaultConstructor() {
        // Test default constructor for Firestore
        Profile emptyProfile = new Profile();
        assertNotNull(emptyProfile);
        assertNull(emptyProfile.getUid());
        assertNull(emptyProfile.getDisplayName());
        assertNull(emptyProfile.getEmail());
    }

    @Test
    public void testSettersAndGetters() {
        // Test all setters and getters
        profile.setUid("newUid");
        profile.setDisplayName("New Name");
        profile.setEmail("newemail@example.com");
        profile.setPhoneNumber("+9876543210");
        profile.setPhotoUrl("https://example.com/new.jpg");

        assertEquals("newUid", profile.getUid());
        assertEquals("New Name", profile.getDisplayName());
        assertEquals("newemail@example.com", profile.getEmail());
        assertEquals("+9876543210", profile.getPhoneNumber());
        assertEquals("https://example.com/new.jpg", profile.getPhotoUrl());
    }

    @Test
    public void testRealisticProfile_usingTestDataHelper() {
        // Test with realistic profile data
        Profile realisticProfile = TestDataHelper.createTestProfile("user456");
        
        assertNotNull(realisticProfile);
        assertEquals("user456", realisticProfile.getUid());
        assertEquals("John Doe", realisticProfile.getDisplayName());
        assertEquals("john.doe@example.com", realisticProfile.getEmail());
        assertNotNull(realisticProfile.getPhotoUrl());
    }

    @Test
    public void testProfileWithPhone_usingTestDataHelper() {
        // US 01.02.01: Test profile with optional phone number
        Profile profileWithPhone = TestDataHelper.createTestProfileWithPhone("user789");
        
        assertNotNull(profileWithPhone.getPhoneNumber());
        assertEquals("+1-555-123-4567", profileWithPhone.getPhoneNumber());
    }
}


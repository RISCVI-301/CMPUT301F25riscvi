package com.example.eventease.data;

import com.example.eventease.model.Profile;
import com.example.eventease.testdata.TestDataHelper;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unit tests for profile management functionality.
 * Tests US 01.02.01 (Provide personal information), US 01.02.02 (Update profile),
 * and US 01.02.04 (Delete profile).
 * 
 * Uses an in-memory implementation to avoid dependencies on Firebase or organizer code.
 */
public class ProfileRepositoryTest {
    
    private InMemoryProfileRepository profileRepo;
    private String testUserId;
    
    @Before
    public void setUp() {
        profileRepo = new InMemoryProfileRepository();
        testUserId = "user123";
    }
    
    @Test
    public void testCreateProfile_withRequiredFields() throws Exception {
        // US 01.02.01: Provide personal information such as name, email and optional phone number
        Profile profile = new Profile(
            testUserId,
            "John Doe",
            "john.doe@example.com",
            "https://example.com/photo.jpg"
        );
        
        Task<Void> createTask = profileRepo.createProfile(profile);
        Tasks.await(createTask);
        
        assertTrue("Create profile task should succeed", createTask.isSuccessful());
        
        // Verify profile was created
        Task<Profile> getTask = profileRepo.getProfile(testUserId);
        Profile retrieved = Tasks.await(getTask);
        
        assertTrue("Get profile task should succeed", getTask.isSuccessful());
        assertNotNull("Profile should not be null", retrieved);
        assertEquals("User ID should match", testUserId, retrieved.getUid());
        assertEquals("Name should match", "John Doe", retrieved.getDisplayName());
        assertEquals("Email should match", "john.doe@example.com", retrieved.getEmail());
    }
    
    @Test
    public void testCreateProfile_withPhoneNumber() throws Exception {
        // US 01.02.01: Phone number is optional
        Profile profile = TestDataHelper.createTestProfileWithPhone(testUserId);
        
        Task<Void> createTask = profileRepo.createProfile(profile);
        Tasks.await(createTask);
        
        assertTrue("Create profile task should succeed", createTask.isSuccessful());
        
        // Verify phone number was saved
        Task<Profile> getTask = profileRepo.getProfile(testUserId);
        Profile retrieved = Tasks.await(getTask);
        
        assertNotNull("Profile should not be null", retrieved);
        assertNotNull("Phone number should not be null", retrieved.getPhoneNumber());
        assertEquals("Phone number should match", "+1-555-123-4567", retrieved.getPhoneNumber());
    }
    
    @Test
    public void testCreateProfile_withoutPhoneNumber() throws Exception {
        // US 01.02.01: Phone number is optional
        Profile profile = TestDataHelper.createTestProfile(testUserId);
        // Phone number not set
        
        Task<Void> createTask = profileRepo.createProfile(profile);
        Tasks.await(createTask);
        
        assertTrue("Create profile task should succeed", createTask.isSuccessful());
        
        // Verify profile exists without phone number
        Task<Profile> getTask = profileRepo.getProfile(testUserId);
        Profile retrieved = Tasks.await(getTask);
        
        assertNotNull("Profile should not be null", retrieved);
        assertNull("Phone number should be null when not provided", retrieved.getPhoneNumber());
    }
    
    @Test
    public void testUpdateProfile_name() throws Exception {
        // US 01.02.02: Update information such as name, email and contact information
        // Create initial profile
        Profile initialProfile = TestDataHelper.createTestProfile(testUserId);
        Tasks.await(profileRepo.createProfile(initialProfile));
        
        // Update name
        Profile update = new Profile();
        update.setUid(testUserId);
        update.setDisplayName("Jane Doe");
        
        Task<Void> updateTask = profileRepo.updateProfile(update);
        Tasks.await(updateTask);
        
        assertTrue("Update profile task should succeed", updateTask.isSuccessful());
        
        // Verify name was updated
        Task<Profile> getTask = profileRepo.getProfile(testUserId);
        Profile retrieved = Tasks.await(getTask);
        
        assertEquals("Name should be updated", "Jane Doe", retrieved.getDisplayName());
        assertEquals("Email should remain unchanged", "john.doe@example.com", retrieved.getEmail());
    }
    
    @Test
    public void testUpdateProfile_email() throws Exception {
        // US 01.02.02: Update email
        Profile initialProfile = TestDataHelper.createTestProfile(testUserId);
        Tasks.await(profileRepo.createProfile(initialProfile));
        
        // Update email
        Profile update = new Profile();
        update.setUid(testUserId);
        update.setEmail("jane.doe@example.com");
        
        Task<Void> updateTask = profileRepo.updateProfile(update);
        Tasks.await(updateTask);
        
        assertTrue("Update profile task should succeed", updateTask.isSuccessful());
        
        // Verify email was updated
        Task<Profile> getTask = profileRepo.getProfile(testUserId);
        Profile retrieved = Tasks.await(getTask);
        
        assertEquals("Email should be updated", "jane.doe@example.com", retrieved.getEmail());
        assertEquals("Name should remain unchanged", "John Doe", retrieved.getDisplayName());
    }
    
    @Test
    public void testUpdateProfile_phoneNumber() throws Exception {
        // US 01.02.02: Update phone number
        Profile initialProfile = TestDataHelper.createTestProfile(testUserId);
        Tasks.await(profileRepo.createProfile(initialProfile));
        
        // Update phone number
        Profile update = new Profile();
        update.setUid(testUserId);
        update.setPhoneNumber("+1-555-999-8888");
        
        Task<Void> updateTask = profileRepo.updateProfile(update);
        Tasks.await(updateTask);
        
        assertTrue("Update profile task should succeed", updateTask.isSuccessful());
        
        // Verify phone number was updated
        Task<Profile> getTask = profileRepo.getProfile(testUserId);
        Profile retrieved = Tasks.await(getTask);
        
        assertEquals("Phone number should be updated", "+1-555-999-8888", retrieved.getPhoneNumber());
    }
    
    @Test
    public void testUpdateProfile_multipleFields() throws Exception {
        // US 01.02.02: Update multiple fields at once
        Profile initialProfile = TestDataHelper.createTestProfile(testUserId);
        Tasks.await(profileRepo.createProfile(initialProfile));
        
        // Update multiple fields
        Profile update = new Profile();
        update.setUid(testUserId);
        update.setDisplayName("Jane Smith");
        update.setEmail("jane.smith@example.com");
        update.setPhoneNumber("+1-555-777-6666");
        update.setPhotoUrl("https://example.com/new-photo.jpg");
        
        Task<Void> updateTask = profileRepo.updateProfile(update);
        Tasks.await(updateTask);
        
        assertTrue("Update profile task should succeed", updateTask.isSuccessful());
        
        // Verify all fields were updated
        Task<Profile> getTask = profileRepo.getProfile(testUserId);
        Profile retrieved = Tasks.await(getTask);
        
        assertEquals("Name should be updated", "Jane Smith", retrieved.getDisplayName());
        assertEquals("Email should be updated", "jane.smith@example.com", retrieved.getEmail());
        assertEquals("Phone number should be updated", "+1-555-777-6666", retrieved.getPhoneNumber());
        assertEquals("Photo URL should be updated", "https://example.com/new-photo.jpg", retrieved.getPhotoUrl());
    }
    
    @Test
    public void testDeleteProfile_success() throws Exception {
        // US 01.02.04: Delete profile if no longer wish to use the app
        // Create profile
        Profile profile = TestDataHelper.createTestProfile(testUserId);
        Tasks.await(profileRepo.createProfile(profile));
        
        // Verify profile exists
        Task<Profile> getBeforeTask = profileRepo.getProfile(testUserId);
        Profile before = Tasks.await(getBeforeTask);
        assertNotNull("Profile should exist before deletion", before);
        
        // Delete profile
        Task<Void> deleteTask = profileRepo.deleteProfile(testUserId);
        Tasks.await(deleteTask);
        
        assertTrue("Delete profile task should succeed", deleteTask.isSuccessful());
        
        // Verify profile no longer exists
        Task<Profile> getAfterTask = profileRepo.getProfile(testUserId);
        Tasks.await(getAfterTask);
        assertFalse("Get profile should fail after deletion", getAfterTask.isSuccessful());
    }
    
    @Test
    public void testDeleteProfile_notFound() throws Exception {
        // Try to delete non-existent profile - should succeed (idempotent)
        Task<Void> deleteTask = profileRepo.deleteProfile("nonexistent");
        Tasks.await(deleteTask);
        
        // Should succeed even if profile doesn't exist (idempotent operation)
        assertTrue("Delete should succeed even if profile doesn't exist", deleteTask.isSuccessful());
    }
    
    @Test
    public void testGetProfile_notFound() throws Exception {
        // Try to get non-existent profile
        Task<Profile> getTask = profileRepo.getProfile("nonexistent");
        Tasks.await(getTask);
        
        assertFalse("Get profile should fail for non-existent profile", getTask.isSuccessful());
        assertNotNull("Should have exception", getTask.getException());
    }
    
    /**
     * In-memory implementation of ProfileRepository for testing.
     */
    private static class InMemoryProfileRepository {
        private final Map<String, Profile> profiles = new ConcurrentHashMap<>();
        
        public Task<Void> createProfile(Profile profile) {
            if (profile.getUid() == null) {
                return Tasks.forException(new Exception("User ID is required"));
            }
            profiles.put(profile.getUid(), profile);
            return Tasks.forResult(null);
        }
        
        public Task<Profile> getProfile(String uid) {
            Profile profile = profiles.get(uid);
            if (profile == null) {
                return Tasks.forException(new Exception("Profile not found"));
            }
            return Tasks.forResult(profile);
        }
        
        public Task<Void> updateProfile(Profile update) {
            if (update.getUid() == null) {
                return Tasks.forException(new Exception("User ID is required"));
            }
            
            Profile existing = profiles.get(update.getUid());
            if (existing == null) {
                return Tasks.forException(new Exception("Profile not found"));
            }
            
            // Update only provided fields
            if (update.getDisplayName() != null) {
                existing.setDisplayName(update.getDisplayName());
            }
            if (update.getEmail() != null) {
                existing.setEmail(update.getEmail());
            }
            if (update.getPhoneNumber() != null) {
                existing.setPhoneNumber(update.getPhoneNumber());
            }
            if (update.getPhotoUrl() != null) {
                existing.setPhotoUrl(update.getPhotoUrl());
            }
            
            return Tasks.forResult(null);
        }
        
        public Task<Void> deleteProfile(String uid) {
            profiles.remove(uid);
            return Tasks.forResult(null);
        }
    }
}


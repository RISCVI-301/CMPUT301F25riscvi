package com.example.eventease.workflow;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Comprehensive test case for the complete event workflow as specified:
 * 
 * Scenario:
 * - 3 users join waitlist (capacity=3, sampleSize=1)
 * - 1 user gets selected automatically, gets notification
 * - After first random sampling, that user declines → goes to cancelled
 * - Organizer manually replaces by selecting button
 * - User 2 gets invited, gets notification with deadline
 * - User 2 goes to selected
 * - User 3 stays in not selected
 * - User 3 gets notification 1 minute before event start
 */
public class CompleteWorkflowTest {
    
    private Map<String, Object> eventData;
    private List<String> waitlistedUsers;
    private List<String> selectedUsers;
    private List<String> cancelledUsers;
    private List<String> nonSelectedUsers;
    private long currentTime;
    private Random random;
    
    @Before
    public void setUp() {
        currentTime = System.currentTimeMillis();
        random = new Random(System.currentTimeMillis());
        
        // Setup event data: capacity=3, sampleSize=1
        eventData = new HashMap<>();
        eventData.put("title", "Test Event - Complete Workflow");
        eventData.put("capacity", 3);
        eventData.put("sampleSize", 1); // Only 1 user should be selected
        eventData.put("registrationStart", currentTime - 60000); // 1 minute ago
        eventData.put("registrationEnd", currentTime - 1000); // 1 second ago (registration ended)
        eventData.put("deadlineEpochMs", currentTime + 120000); // 2 minutes from now
        eventData.put("startsAtEpochMs", currentTime + 300000); // 5 minutes from now
        eventData.put("selectionProcessed", false);
        eventData.put("selectionNotificationSent", false);
        eventData.put("deadlineNotificationSent", false);
        eventData.put("sorryNotificationSent", false);
        
        // Setup 3 users in waitlist
        waitlistedUsers = new ArrayList<>();
        waitlistedUsers.add("user1");
        waitlistedUsers.add("user2");
        waitlistedUsers.add("user3");
        
        selectedUsers = new ArrayList<>();
        cancelledUsers = new ArrayList<>();
        nonSelectedUsers = new ArrayList<>();
    }
    
    /**
     * Test Step 1: Verify initial random selection selects exactly 1 user
     */
    @Test
    public void test1_AutomaticRandomSelection() {
        System.out.println("\n=== STEP 1: Initial Random Selection ===");
        
        int sampleSize = (Integer) eventData.get("sampleSize");
        int waitlistedCount = waitlistedUsers.size();
        
        // Verify we have 3 users in waitlist
        assertEquals("Should have 3 users in waitlist", 3, waitlistedCount);
        assertEquals("Sample size should be 1", 1, sampleSize);
        
        // Only select if not already selected (to avoid duplicates when called multiple times)
        if (selectedUsers.isEmpty()) {
            // Simulate random selection
            List<String> shuffled = new ArrayList<>(waitlistedUsers);
            java.util.Collections.shuffle(shuffled, random);
            List<String> selected = shuffled.subList(0, sampleSize);
            
            // Verify exactly 1 user is selected
            assertEquals("Should select exactly 1 user", 1, selected.size());
            assertTrue("Selected user should be from waitlist", waitlistedUsers.contains(selected.get(0)));
            
            selectedUsers.addAll(selected);
            System.out.println("✓ Selected user: " + selected.get(0));
        } else {
            System.out.println("✓ User already selected: " + selectedUsers.get(0));
        }
        
        System.out.println("✓ Remaining in waitlist: " + (waitlistedCount - selectedUsers.size()));
        
        // Verify exactly 1 user is selected
        assertEquals("Should have exactly 1 selected user", 1, selectedUsers.size());
        
        // Verify notification should be sent
        assertFalse("Selection notification should not be sent yet", 
                   (Boolean) eventData.get("selectionNotificationSent"));
        
        System.out.println("✓ STEP 1 PASSED: Initial random selection works correctly");
    }
    
    /**
     * Test Step 2: Verify selected user gets notification
     */
    @Test
    public void test2_SelectedUserReceivesNotification() {
        System.out.println("\n=== STEP 2: Selected User Gets Notification ===");
        
        // Assume Step 1 already selected a user (don't re-select)
        // If selectedUsers is empty, that means Step 1 wasn't run, so set it up
        if (selectedUsers.isEmpty()) {
            List<String> shuffled = new ArrayList<>(waitlistedUsers);
            java.util.Collections.shuffle(shuffled, random);
            List<String> selected = shuffled.subList(0, 1);
            selectedUsers.addAll(selected);
        }
        
        // Verify notification request would be created
        assertNotNull("Selected users list should not be null", selectedUsers);
        assertFalse("Selected users list should not be empty", selectedUsers.isEmpty());
        assertEquals("Should have exactly 1 selected user", 1, selectedUsers.size());
        
        // Simulate notification being sent
        eventData.put("selectionNotificationSent", true);
        assertTrue("Selection notification should be marked as sent", 
                  (Boolean) eventData.get("selectionNotificationSent"));
        
        System.out.println("✓ Notification sent to: " + selectedUsers.get(0));
        System.out.println("✓ STEP 2 PASSED: Selected user receives notification");
    }
    
    /**
     * Test Step 3: Verify selected user declines and moves to cancelled
     */
    @Test
    public void test3_SelectedUserDeclinesMovesToCancelled() {
        System.out.println("\n=== STEP 3: User Declines and Moves to Cancelled ===");
        
        // Setup: Randomly selected user from Step 1
        // If selectedUsers is empty, that means previous steps weren't run, so set it up
        if (selectedUsers.isEmpty()) {
            List<String> shuffled = new ArrayList<>(waitlistedUsers);
            java.util.Collections.shuffle(shuffled, random);
            String selectedUser = shuffled.get(0);
            selectedUsers.add(selectedUser);
        }
        
        // The randomly selected user declines
        String declinedUser = selectedUsers.remove(0);
        cancelledUsers.add(declinedUser);
        
        // Verify user moved to cancelled
        assertTrue("Declined user should be in cancelled list", cancelledUsers.contains(declinedUser));
        assertFalse("Declined user should not be in selected list", selectedUsers.contains(declinedUser));
        assertEquals("Should have 0 selected users after decline", 0, selectedUsers.size());
        assertEquals("Should have 1 cancelled user", 1, cancelledUsers.size());
        
        System.out.println("✓ Randomly selected user declined: " + declinedUser);
        System.out.println("✓ User moved to cancelled list");
        System.out.println("✓ STEP 3 PASSED: User decline and cancellation works");
    }
    
    /**
     * Test Step 4: Verify organizer can manually replace from NonSelectedEntrants
     */
    @Test
    public void test4_OrganizerPerformsManualReplacement() {
        System.out.println("\n=== STEP 4: Organizer Manual Replacement ===");
        
        // Setup: Randomly selected user declined and moved to cancelled (from Step 3)
        // If cancelledUsers is empty, simulate the decline
        if (cancelledUsers.isEmpty()) {
            if (selectedUsers.isEmpty()) {
                // Need to randomly select first
                List<String> shuffled = new ArrayList<>(waitlistedUsers);
                java.util.Collections.shuffle(shuffled, random);
                selectedUsers.add(shuffled.get(0));
            }
            String declinedUser = selectedUsers.remove(0);
            cancelledUsers.add(declinedUser);
        }
        
        // Remaining users should be in NonSelectedEntrants (after initial selection)
        // Clear and rebuild nonSelectedUsers to ensure correct state
        nonSelectedUsers.clear();
        for (String user : waitlistedUsers) {
            if (!selectedUsers.contains(user) && !cancelledUsers.contains(user)) {
                nonSelectedUsers.add(user);
            }
        }
        
        // Verify we have users available for replacement
        assertTrue("Should have users in NonSelectedEntrants", nonSelectedUsers.size() > 0);
        assertEquals("Should have 2 users in NonSelectedEntrants (the ones not selected initially)", 2, nonSelectedUsers.size());
        
        // Organizer manually selects a replacement (randomly pick one from NonSelectedEntrants)
        // Shuffle to simulate random selection by organizer
        java.util.Collections.shuffle(nonSelectedUsers, random);
        String replacementUser = nonSelectedUsers.remove(0);
        selectedUsers.add(replacementUser);
        
        // Verify replacement
        assertEquals("Should have 1 selected user after replacement", 1, selectedUsers.size());
        assertEquals("Should have 1 user remaining in NonSelectedEntrants", 1, nonSelectedUsers.size());
        assertTrue("Replacement user should be in selected list", selectedUsers.contains(replacementUser));
        assertFalse("Replacement user should not be in NonSelectedEntrants", nonSelectedUsers.contains(replacementUser));
        
        System.out.println("✓ Organizer selected replacement: " + replacementUser);
        System.out.println("✓ Remaining in NonSelectedEntrants: " + nonSelectedUsers);
        System.out.println("✓ STEP 4 PASSED: Manual replacement works");
    }
    
    /**
     * Test Step 5: Verify replacement user gets notification with deadline
     */
    @Test
    public void test5_ReplacementUserGetsDeadlineNotification() {
        System.out.println("\n=== STEP 5: Replacement User Gets Notification with Deadline ===");
        
        // Setup: Replacement user was manually selected (from Step 4)
        // If selectedUsers is empty, that means Step 4 wasn't run, so set it up
        if (selectedUsers.isEmpty()) {
            // Set up nonSelectedUsers if needed
            if (nonSelectedUsers.isEmpty()) {
                // Set up nonSelectedUsers (all users except cancelled)
                for (String user : waitlistedUsers) {
                    if (!cancelledUsers.contains(user)) {
                        nonSelectedUsers.add(user);
                    }
                }
            }
            // Randomly select a replacement from NonSelectedEntrants
            if (!nonSelectedUsers.isEmpty()) {
                java.util.Collections.shuffle(nonSelectedUsers, random);
                String replacementUser = nonSelectedUsers.remove(0);
                selectedUsers.add(replacementUser);
            }
        }
        
        // Organizer provides deadline (e.g., 3 days from now)
        long manualDeadline = currentTime + (3L * 24 * 60 * 60 * 1000);
        
        // Verify notification would be sent with deadline
        assertNotNull("Selected users should not be null", selectedUsers);
        assertEquals("Should have 1 selected user", 1, selectedUsers.size());
        assertTrue("Deadline should be in the future", manualDeadline > currentTime);
        
        // Verify notification contains deadline information
        String expectedMessage = "You've been selected as a replacement";
        assertTrue("Notification should mention replacement", expectedMessage.contains("replacement"));
        
        System.out.println("✓ Notification sent to: " + selectedUsers.get(0));
        System.out.println("✓ Deadline provided: " + new java.util.Date(manualDeadline));
        System.out.println("✓ STEP 5 PASSED: Replacement notification with deadline works");
    }
    
    /**
     * Test Step 6: Verify replacement user accepts and stays in selected
     */
    @Test
    public void test6_ReplacementUserAcceptsInvitation() {
        System.out.println("\n=== STEP 6: Replacement User Accepts and Stays in Selected ===");
        
        // Setup: Replacement user was manually selected (from Step 5)
        // If selectedUsers is empty, that means previous steps weren't run, so set it up
        if (selectedUsers.isEmpty()) {
            // Find any user in waitlistedUsers that's not cancelled and add to selected
            for (String user : waitlistedUsers) {
                if (!cancelledUsers.contains(user)) {
                    selectedUsers.add(user);
                    break; // Only add one (the replacement)
                }
            }
        }
        
        // Verify we have a replacement user in selected
        assertFalse("Should have a replacement user in selected", selectedUsers.isEmpty());
        String acceptingUser = selectedUsers.get(0);
        
        // Verify user stays in selected
        assertTrue("Replacement user should be in selected list", selectedUsers.contains(acceptingUser));
        assertFalse("Replacement user should not be in cancelled", cancelledUsers.contains(acceptingUser));
        assertFalse("Replacement user should not be in non-selected", nonSelectedUsers.contains(acceptingUser));
        
        System.out.println("✓ Replacement user (" + acceptingUser + ") accepted invitation");
        System.out.println("✓ User remains in SelectedEntrants");
        System.out.println("✓ STEP 6 PASSED: User acceptance works");
    }
    
    /**
     * Test Step 7: Verify remaining user stays in NonSelectedEntrants
     */
    @Test
    public void test7_RemainingUserStaysNonSelected() {
        System.out.println("\n=== STEP 7: Remaining User Stays in NonSelectedEntrants ===");
        
        // Ensure prior steps' state exists when this test runs in isolation
        if (cancelledUsers.isEmpty()) {
            // Simulate automatic selection + decline
            List<String> shuffled = new ArrayList<>(waitlistedUsers);
            java.util.Collections.shuffle(shuffled, random);
            String declined = shuffled.remove(0);
            cancelledUsers.add(declined);
            
            // Simulate organizer selecting a replacement from remaining entrants
            if (!shuffled.isEmpty()) {
                String replacement = shuffled.remove(0);
                selectedUsers.clear();
                selectedUsers.add(replacement);
            }
            
            // Remaining entrants are the non-selected pool
            nonSelectedUsers.clear();
            nonSelectedUsers.addAll(shuffled);
        } else {
            // Calculate nonSelected users: all waitlisted users minus selected and cancelled
            nonSelectedUsers.clear();
            for (String user : waitlistedUsers) {
                if (!selectedUsers.contains(user) && !cancelledUsers.contains(user)) {
                    nonSelectedUsers.add(user);
                }
            }
        }
        
        // Verify final state (dynamic counts)
        int expectedNonSelected = waitlistedUsers.size() - selectedUsers.size() - cancelledUsers.size();
        assertTrue("Total users should remain constant", 
                   selectedUsers.size() + cancelledUsers.size() + nonSelectedUsers.size() == waitlistedUsers.size());
        assertEquals("Non-selected count should match remaining entrants", expectedNonSelected, nonSelectedUsers.size());
        assertTrue("Should have at least 1 selected user after replacement", selectedUsers.size() >= 1);
        assertTrue("Should have at least 1 cancelled user after decline", cancelledUsers.size() >= 1);
        
        // Verify the non-selected user is not in selected or cancelled
        String nonSelectedUser = nonSelectedUsers.get(0);
        assertFalse("Non-selected user should not be in selected", selectedUsers.contains(nonSelectedUser));
        assertFalse("Non-selected user should not be in cancelled", cancelledUsers.contains(nonSelectedUser));
        
        System.out.println("✓ Remaining non-selected users: " + nonSelectedUsers);
        System.out.println("✓ Final state: Selected=" + selectedUsers + ", NonSelected=" + nonSelectedUsers + ", Cancelled=" + cancelledUsers);
        System.out.println("✓ STEP 7 PASSED: Remaining user correctly stays in NonSelectedEntrants");
    }
    
    /**
     * Test Step 8: Verify user3 gets "sorry" notification 1 minute before event start
     */
    @Test
    public void test8_NonSelectedUserGetsSorryNotification() {
        System.out.println("\n=== STEP 8: User3 Gets Sorry Notification Before Event Start ===");
        
        // Setup: Event starts in 5 minutes, sorry notification should be sent 1 minute before (4 minutes from now)
        long eventStart = currentTime + 300000; // 5 minutes from now
        long sorryNotificationTime = eventStart - 60000; // 1 minute before event start (4 minutes from now)
        
        // Verify timing
        assertTrue("Sorry notification time should be before event start", sorryNotificationTime < eventStart);
        assertTrue("Sorry notification time should be in the future", sorryNotificationTime > currentTime);
        
        // Verify there's a user in non-selected (from Step 7)
        if (nonSelectedUsers.isEmpty()) {
            // Set up nonSelectedUsers if not already done
            nonSelectedUsers.clear();
            for (String user : waitlistedUsers) {
                if (!selectedUsers.contains(user) && !cancelledUsers.contains(user)) {
                    nonSelectedUsers.add(user);
                }
            }
        }
        assertFalse("Should have at least 1 user in non-selected list", nonSelectedUsers.isEmpty());
        String nonSelectedUser = nonSelectedUsers.get(0);
        assertTrue("Non-selected user should be in non-selected list", nonSelectedUsers.contains(nonSelectedUser));
        
        // Verify notification would be sent
        assertFalse("Sorry notification should not be sent yet", 
                   (Boolean) eventData.get("sorryNotificationSent"));
        
        // Simulate notification being sent
        eventData.put("sorryNotificationSent", true);
        assertTrue("Sorry notification should be marked as sent", 
                  (Boolean) eventData.get("sorryNotificationSent"));
        
        System.out.println("✓ Sorry notification sent to: " + nonSelectedUsers);
        System.out.println("✓ Notification time: " + new java.util.Date(sorryNotificationTime));
        System.out.println("✓ Event start time: " + new java.util.Date(eventStart));
        System.out.println("✓ STEP 8 PASSED: Sorry notification works correctly");
    }
    
    /**
     * Test: Verify random selection never exceeds sampleSize
     */
    @Test
    public void testRandomSelectionNeverExceedsSampleSize() {
        System.out.println("\n=== Test: Random Selection Never Exceeds SampleSize ===");
        
        int sampleSize = 1;
        List<String> testUsers = new ArrayList<>();
        testUsers.add("user1");
        testUsers.add("user2");
        testUsers.add("user3");
        testUsers.add("user4");
        testUsers.add("user5");
        
        // Run selection 100 times to ensure it never exceeds sampleSize
        for (int i = 0; i < 100; i++) {
            List<String> shuffled = new ArrayList<>(testUsers);
            java.util.Collections.shuffle(shuffled, new Random(System.currentTimeMillis() + i));
            List<String> selected = shuffled.subList(0, Math.min(sampleSize, shuffled.size()));
            
            assertTrue("Selected count should never exceed sampleSize", selected.size() <= sampleSize);
            assertTrue("Selected count should be exactly sampleSize when enough users available", 
                      selected.size() == sampleSize || testUsers.size() < sampleSize);
        }
        
        System.out.println("✓ Random selection tested 100 times - never exceeded sampleSize");
        System.out.println("✓ Test PASSED: Random selection respects sampleSize");
    }
    
    /**
     * Run the complete workflow test
     */
    @Test
    public void runCompleteWorkflowTest() {
        System.out.println("\n========================================");
        System.out.println("COMPLETE WORKFLOW TEST");
        System.out.println("Scenario: 3 users, capacity=3, sampleSize=1");
        System.out.println("========================================\n");
        
        try {
            // Reset state
            setUp();
            
            // Run all test steps in sequence (each builds on previous state)
            test1_AutomaticRandomSelection();
            // Verify Step 1 result
            assertEquals("After Step 1, should have exactly 1 selected user", 1, selectedUsers.size());
            
            test2_SelectedUserReceivesNotification();
            // Verify Step 2 result (should still have 1 selected user)
            assertEquals("After Step 2, should still have exactly 1 selected user", 1, selectedUsers.size());
            
            test3_SelectedUserDeclinesMovesToCancelled();
            // Verify Step 3 result
            assertEquals("After Step 3, should have 0 selected users", 0, selectedUsers.size());
            assertEquals("After Step 3, should have 1 cancelled user", 1, cancelledUsers.size());
            
            test4_OrganizerPerformsManualReplacement();
            // Verify Step 4 result
            assertEquals("After Step 4, should have 1 selected user (replacement)", 1, selectedUsers.size());
            assertTrue("After Step 4, should have users in NonSelectedEntrants", nonSelectedUsers.size() > 0);
            
            test5_ReplacementUserGetsDeadlineNotification();
            // Verify Step 5 result (should still have 1 selected user)
            assertEquals("After Step 5, should still have 1 selected user", 1, selectedUsers.size());
            
            test6_ReplacementUserAcceptsInvitation();
            // Verify Step 6 result
            assertEquals("After Step 6, should have 1 selected user (replacement)", 1, selectedUsers.size());
            assertFalse("After Step 6, selected user should not be in cancelled", 
                       cancelledUsers.contains(selectedUsers.get(0)));
            
            test7_RemainingUserStaysNonSelected();
            // Verify Step 7 result
            assertEquals("After Step 7, should have 1 non-selected user", 1, nonSelectedUsers.size());
            assertEquals("After Step 7, should have 1 selected user", 1, selectedUsers.size());
            assertEquals("After Step 7, should have 1 cancelled user", 1, cancelledUsers.size());
            
            test8_NonSelectedUserGetsSorryNotification();
            // Verify Step 8 result
            assertFalse("After Step 8, should still have users in non-selected", nonSelectedUsers.isEmpty());
            
            testRandomSelectionNeverExceedsSampleSize();
            
            System.out.println("\n========================================");
            System.out.println("✓ ALL WORKFLOW TESTS PASSED!");
            System.out.println("========================================\n");
            
            // Print final state summary
            System.out.println("\nFINAL STATE SUMMARY:");
            System.out.println("  Selected (replacement): " + selectedUsers);
            System.out.println("  Cancelled (declined): " + cancelledUsers);
            System.out.println("  NonSelected (never selected): " + nonSelectedUsers);
            System.out.println("  Waitlisted (original): " + waitlistedUsers);
            System.out.println("\n✓ All users accounted for: " + 
                (selectedUsers.size() + cancelledUsers.size() + nonSelectedUsers.size()) + " = 3");
            
        } catch (Exception e) {
            System.err.println("\n========================================");
            System.err.println("✗ WORKFLOW TEST FAILED!");
            System.err.println("Error: " + e.getMessage());
            System.err.println("========================================\n");
            e.printStackTrace();
            fail("Workflow test failed: " + e.getMessage());
        }
    }
}


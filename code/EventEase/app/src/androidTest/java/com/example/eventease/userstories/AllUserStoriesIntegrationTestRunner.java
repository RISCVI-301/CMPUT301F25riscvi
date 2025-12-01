package com.example.eventease.userstories;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * Test runner that executes all user story INTEGRATION test suites.
 * 
 * ⚠️ These are REAL Firebase/Firestore integration tests!
 * They interact with actual Firebase and send real notifications.
 * 
 * This runner executes:
 * - OrganizerUserStoryIntegrationTests: All organizer user stories (US 02.x.x) - REAL Firebase
 * - AdminUserStoryIntegrationTests: All admin user stories (US 03.x.x) - REAL Firebase
 * 
 * Usage:
 * 1. Run from Android Studio: Right-click → Run 'AllUserStoriesIntegrationTestRunner'
 * 2. Run from command line: ./gradlew connectedAndroidTest --tests AllUserStoriesIntegrationTestRunner
 * 
 * ⚠️ REQUIRES: Connected Android device/emulator with internet connection
 */
public class AllUserStoriesIntegrationTestRunner {
    
    public static void main(String[] args) {
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     EventEase User Story INTEGRATION Test Suite Runner    ║");
        System.out.println("║            (REAL Firebase/Firestore Tests)                 ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
        System.out.println("⚠️  WARNING: These tests use REAL Firebase!");
        System.out.println("⚠️  They create, read, update, and delete real Firestore documents");
        System.out.println("⚠️  They send real FCM notifications");
        System.out.println("\n");
        
        // Run all integration test classes
        Result result = JUnitCore.runClasses(
            OrganizerUserStoryIntegrationTests.class,
            AdminUserStoryIntegrationTests.class
        );
        
        // Print results
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            Integration Test Results Summary                ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
        System.out.println("Tests Run: " + result.getRunCount());
        System.out.println("Tests Passed: " + (result.getRunCount() - result.getFailureCount()));
        System.out.println("Tests Failed: " + result.getFailureCount());
        System.out.println("Execution Time: " + result.getRunTime() + " ms");
        System.out.println("\n");
        
        if (result.wasSuccessful()) {
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║       ✓ ALL INTEGRATION TESTS PASSED - FIREBASE WORKS!     ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝");
            System.out.println("\n");
            System.out.println("✅ All user stories work with REAL Firebase!");
            System.out.println("✅ Firestore operations verified");
            System.out.println("✅ FCM notifications verified");
            System.out.println("\n");
        } else {
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║              ✗ SOME INTEGRATION TESTS FAILED!              ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝");
            System.out.println("\nFailures:\n");
            
            for (Failure failure : result.getFailures()) {
                System.out.println("─────────────────────────────────────────────────────────────");
                System.out.println("Test: " + failure.getTestHeader());
                System.out.println("Message: " + failure.getMessage());
                System.out.println("Trace:");
                System.out.println(failure.getTrace());
                System.out.println("─────────────────────────────────────────────────────────────\n");
            }
        }
        
        System.out.println("\n");
        
        // Exit with appropriate code
        System.exit(result.wasSuccessful() ? 0 : 1);
    }
}


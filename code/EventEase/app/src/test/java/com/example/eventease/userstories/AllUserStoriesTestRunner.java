package com.example.eventease.userstories;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * UNIT TEST runner that executes all user story UNIT test suites.
 * 
 * ⚠️ This runner executes UNIT tests only (in-memory/mock data).
 * For REAL Firebase integration tests, use the integration test runners.
 * 
 * This runner executes:
 * - EntrantUserStoryUnitTests: All entrant user stories (US 01.x.x) - UNIT tests
 * - OrganizerUserStoryUnitTests: All organizer user stories (US 02.x.x) - UNIT tests
 * - AdminUserStoryUnitTests: All admin user stories (US 03.x.x) - UNIT tests
 * 
 * Usage:
 * 1. Run from Android Studio: Right-click → Run 'AllUserStoriesTestRunner'
 * 2. Run from command line: ./gradlew test --tests AllUserStoriesTestRunner
 * 3. Run specific suite: ./gradlew test --tests EntrantUserStoryTests
 */
public class AllUserStoriesTestRunner {
    
    public static void main(String[] args) {
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║      EventEase User Story UNIT Test Suite Runner          ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
        
        // Run all UNIT test classes
        Result result = JUnitCore.runClasses(
            EntrantUserStoryUnitTests.class,
            OrganizerUserStoryUnitTests.class,
            AdminUserStoryUnitTests.class
        );
        
        // Print results
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                    Test Results Summary                    ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
        System.out.println("Tests Run: " + result.getRunCount());
        System.out.println("Tests Passed: " + (result.getRunCount() - result.getFailureCount()));
        System.out.println("Tests Failed: " + result.getFailureCount());
        System.out.println("Execution Time: " + result.getRunTime() + " ms");
        System.out.println("\n");
        
        if (result.wasSuccessful()) {
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║          ✓ ALL USER STORY UNIT TESTS PASSED!             ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝");
        } else {
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║                 ✗ SOME TESTS FAILED!                      ║");
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


package com.example.eventease;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import com.example.eventease.userstories.EntrantUserStoryTests;
import com.example.eventease.userstories.OrganizerUserStoryTests;
import com.example.eventease.userstories.AdminUserStoryTests;
import com.example.eventease.edgecases.EdgeCaseTests;
import com.example.eventease.edgecases.ErrorHandlingTests;
import com.example.eventease.edgecases.IntegrationTests;

/**
 * Master test runner that executes ALL test suites.
 * 
 * This runner executes:
 * - User Story Tests (78 tests)
 * - Edge Case Tests (30+ tests)
 * - Error Handling Tests (20+ tests)
 * - Integration Tests (7+ tests)
 * 
 * Total: 130+ comprehensive tests
 * 
 * Usage:
 * 1. Run from Android Studio: Right-click → Run 'AllTestsRunner'
 * 2. Run from command line: ./gradlew test --tests AllTestsRunner
 */
public class AllTestsRunner {
    
    public static void main(String[] args) {
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            EventEase COMPLETE TEST SUITE RUNNER            ║");
        System.out.println("║         Production-Ready Validation Test Suite             ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
        System.out.println("Running comprehensive test suite...");
        System.out.println("  • User Story Tests (78 tests)");
        System.out.println("  • Edge Case Tests (30+ tests)");
        System.out.println("  • Error Handling Tests (20+ tests)");
        System.out.println("  • Integration Tests (7+ tests)");
        System.out.println("\n");
        
        // Run all test classes
        Result result = JUnitCore.runClasses(
            // User Story Tests
            EntrantUserStoryTests.class,
            OrganizerUserStoryTests.class,
            AdminUserStoryTests.class,
            // Edge Case Tests
            EdgeCaseTests.class,
            ErrorHandlingTests.class,
            IntegrationTests.class
        );
        
        // Print results
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              COMPLETE TEST RESULTS SUMMARY                 ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
        System.out.println("Total Tests Run: " + result.getRunCount());
        System.out.println("Tests Passed: " + (result.getRunCount() - result.getFailureCount()));
        System.out.println("Tests Failed: " + result.getFailureCount());
        System.out.println("Success Rate: " + 
            String.format("%.1f%%", 
                ((double)(result.getRunCount() - result.getFailureCount()) / result.getRunCount()) * 100));
        System.out.println("Total Execution Time: " + result.getRunTime() + " ms");
        System.out.println("\n");
        
        if (result.wasSuccessful()) {
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║                                                              ║");
            System.out.println("║        ✓✓✓ ALL TESTS PASSED - READY FOR LAUNCH! ✓✓✓       ║");
            System.out.println("║                                                              ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝");
            System.out.println("\n");
            System.out.println("Your app has passed comprehensive testing:");
            System.out.println("  ✓ All user stories validated");
            System.out.println("  ✓ Edge cases handled gracefully");
            System.out.println("  ✓ Error scenarios handled properly");
            System.out.println("  ✓ Integration flows working correctly");
            System.out.println("\n");
        } else {
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║                                                              ║");
            System.out.println("║            ✗ SOME TESTS FAILED - REVIEW NEEDED             ║");
            System.out.println("║                                                              ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝");
            System.out.println("\nFailures (" + result.getFailureCount() + "):\n");
            
            int failureNum = 1;
            for (Failure failure : result.getFailures()) {
                System.out.println("─────────────────────────────────────────────────────────────");
                System.out.println("FAILURE #" + failureNum++);
                System.out.println("Test: " + failure.getTestHeader());
                System.out.println("Message: " + failure.getMessage());
                if (failure.getException() != null) {
                    System.out.println("Exception: " + failure.getException().getClass().getSimpleName());
                }
                System.out.println("─────────────────────────────────────────────────────────────\n");
            }
            
            System.out.println("\nPlease review and fix the failures above before launching.\n");
        }
        
        System.out.println("\n");
        
        // Exit with appropriate code
        System.exit(result.wasSuccessful() ? 0 : 1);
    }
}


package com.example.eventease.edgecases;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * Test runner that executes all edge case and error handling test suites.
 * 
 * This runner executes:
 * - EdgeCaseTests: Null/empty data, boundaries, invalid input, concurrent ops, state transitions
 * - ErrorHandlingTests: Network errors, permissions, validation, exceptions
 * 
 * Usage:
 * 1. Run from Android Studio: Right-click → Run 'AllEdgeCaseTestsRunner'
 * 2. Run from command line: ./gradlew test --tests AllEdgeCaseTestsRunner
 */
public class AllEdgeCaseTestsRunner {
    
    public static void main(String[] args) {
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║    EventEase Edge Case, Error Handling & Integration Tests ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
        
        // Run all test classes
        Result result = JUnitCore.runClasses(
            EdgeCaseTests.class,
            ErrorHandlingTests.class,
            IntegrationTests.class
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
            System.out.println("║        ✓ ALL EDGE CASE & ERROR HANDLING TESTS PASSED!     ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝");
        } else {
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║              ✗ SOME EDGE CASE TESTS FAILED!               ║");
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


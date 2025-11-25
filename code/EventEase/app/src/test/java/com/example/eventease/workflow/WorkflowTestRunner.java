package com.example.eventease.workflow;

import android.util.Log;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * Test runner that executes all workflow tests and provides a summary.
 * 
 * Run this class to execute all automated tests for the event workflow.
 * 
 * Usage:
 * 1. Run from Android Studio: Right-click → Run 'WorkflowTestRunner'
 * 2. Run from command line: ./gradlew test
 * 3. Run from terminal: adb shell am instrument -w com.example.eventease.test/androidx.test.runner.AndroidJUnitRunner
 */
public class WorkflowTestRunner {
    
    private static final String TAG = "WorkflowTestRunner";
    
    public static void main(String[] args) {
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     EventEase Workflow Automated Test Suite              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
        
        // Run all test classes
        Result result = JUnitCore.runClasses(
            EventWorkflowTest.class,
            WorkflowIntegrationTest.class
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
        System.out.println("Run Time: " + result.getRunTime() + " ms");
        System.out.println("\n");
        
        if (result.wasSuccessful()) {
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║              ✓ ALL TESTS PASSED SUCCESSFULLY!              ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝");
        } else {
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║                    ✗ SOME TESTS FAILED                      ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝");
            System.out.println("\nFailures:\n");
            for (Failure failure : result.getFailures()) {
                System.out.println("Test: " + failure.getTestHeader());
                System.out.println("Message: " + failure.getMessage());
                System.out.println("Trace: " + failure.getTrace());
                System.out.println("\n");
            }
        }
        
        System.out.println("\n");
        
        // Exit with appropriate code
        System.exit(result.wasSuccessful() ? 0 : 1);
    }
}


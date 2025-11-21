package com.example.eventease.logic;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for EventValidator class.
 * Tests event validation logic for US 02.01.01 (Create event) and US 02.01.04 (Set registration period).
 */
public class EventValidatorTest {

    @Test
    public void testValidateTitle_validTitle() {
        // US 02.01.01: Valid title should pass
        assertNull(EventValidator.validateTitle("Test Event"));
        assertNull(EventValidator.validateTitle("My Awesome Event"));
    }

    @Test
    public void testValidateTitle_emptyTitle() {
        // US 02.01.01: Empty title should fail
        assertNotNull(EventValidator.validateTitle(null));
        assertNotNull(EventValidator.validateTitle(""));
        assertNotNull(EventValidator.validateTitle("   "));
    }

    @Test
    public void testValidateTitle_tooLong() {
        // US 02.01.01: Title over 80 characters should fail
        String longTitle = "a".repeat(81);
        assertNotNull(EventValidator.validateTitle(longTitle));
    }

    @Test
    public void testValidateTitle_exactly80Characters() {
        // US 02.01.01: Title exactly 80 characters should pass
        String exactly80 = "a".repeat(80);
        assertNull(EventValidator.validateTitle(exactly80));
    }

    @Test
    public void testValidateCapacity_validCapacity() {
        // US 02.03.01: Valid capacity should pass
        assertNull(EventValidator.validateCapacity("1"));
        assertNull(EventValidator.validateCapacity("50"));
        assertNull(EventValidator.validateCapacity("500"));
    }

    @Test
    public void testValidateCapacity_emptyCapacity() {
        // US 02.03.01: Empty capacity should fail
        assertNotNull(EventValidator.validateCapacity(null));
        assertNotNull(EventValidator.validateCapacity(""));
        assertNotNull(EventValidator.validateCapacity("   "));
    }

    @Test
    public void testValidateCapacity_tooSmall() {
        // US 02.03.01: Capacity less than 1 should fail
        assertNotNull(EventValidator.validateCapacity("0"));
        assertNotNull(EventValidator.validateCapacity("-1"));
    }

    @Test
    public void testValidateCapacity_tooLarge() {
        // US 02.03.01: Capacity over 500 should fail
        assertNotNull(EventValidator.validateCapacity("501"));
        assertNotNull(EventValidator.validateCapacity("1000"));
    }

    @Test
    public void testValidateCapacity_notANumber() {
        // US 02.03.01: Non-numeric capacity should fail
        assertNotNull(EventValidator.validateCapacity("abc"));
        assertNotNull(EventValidator.validateCapacity("12.5"));
        assertNotNull(EventValidator.validateCapacity("50a"));
    }

    @Test
    public void testValidateCapacity_boundaryValues() {
        // US 02.03.01: Test boundary values
        assertNull(EventValidator.validateCapacity("1"));  // Minimum valid
        assertNull(EventValidator.validateCapacity("500")); // Maximum valid
        assertNotNull(EventValidator.validateCapacity("0")); // Below minimum
        assertNotNull(EventValidator.validateCapacity("501")); // Above maximum
    }

    @Test
    public void testValidateWhen_futureTime() {
        // US 02.01.04: Future time should pass
        long futureTime = System.currentTimeMillis() + 86400000L; // Tomorrow
        assertNull(EventValidator.validateWhen(futureTime));
    }

    @Test
    public void testValidateWhen_pastTime() {
        // US 02.01.04: Past time should fail
        long pastTime = System.currentTimeMillis() - 86400000L; // Yesterday
        assertNotNull(EventValidator.validateWhen(pastTime));
    }

    @Test
    public void testValidateWhen_currentTime() {
        // US 02.01.04: Current time should fail (must be in future)
        long currentTime = System.currentTimeMillis();
        assertNotNull(EventValidator.validateWhen(currentTime));
    }

    @Test
    public void testValidateCapacity_withWhitespace() {
        // Test that capacity validation trims whitespace
        assertNull(EventValidator.validateCapacity("  50  "));
        assertNull(EventValidator.validateCapacity(" 1 "));
    }
}


package com.example.eventease.logic;

/**
 * Utility class for validating event data.
 * 
 * <p>This class provides static methods to validate event fields such as title, capacity, and timing.
 * All validation methods return null if the validation passes, or an error message string if validation fails.
 * 
 * <p>This class follows the utility class pattern with a private constructor to prevent instantiation.
 */
public final class EventValidator {
    /**
     * Private constructor to prevent instantiation.
     */
    private EventValidator(){}

    /**
     * Validates an event title.
     * 
     * @param s the title string to validate
     * @return null if valid, or an error message if invalid
     */
    public static String validateTitle(String s) {
        if (s == null || s.trim().isEmpty()) return "Title is required";
        if (s.length() > 80) return "Max 80 characters";
        return null;
    }

    /**
     * Validates event capacity.
     * Capacity must be a number between 1 and 500.
     * 
     * @param s the capacity string to validate
     * @return null if valid, or an error message if invalid
     */
    public static String validateCapacity(String s) {
        if (s == null || s.trim().isEmpty()) return "Capacity required";
        try {
            int v = Integer.parseInt(s.trim());
            if (v < 1 || v > 500) return "Capacity must be 1–500";
            return null;
        } catch (NumberFormatException e) {
            return "Enter a number";
        }
    }

    /**
     * Validates that an event time is in the future.
     * 
     * @param epochMs the event time in milliseconds since epoch
     * @return null if valid (time is in the future), or an error message if invalid
     */
    public static String validateWhen(long epochMs) {
        long now = System.currentTimeMillis();
        if (epochMs <= now) return "Start must be in the future";
        return null;
    }
}
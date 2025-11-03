package com.example.eventease.logic;

public final class EventValidator {
    private EventValidator(){}

    public static String validateTitle(String s) {
        if (s == null || s.trim().isEmpty()) return "Title is required";
        if (s.length() > 80) return "Max 80 characters";
        return null;
    }

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

    public static String validateWhen(long epochMs) {
        long now = System.currentTimeMillis();
        if (epochMs < now) return "Start must be in the future";
        return null;
    }
}
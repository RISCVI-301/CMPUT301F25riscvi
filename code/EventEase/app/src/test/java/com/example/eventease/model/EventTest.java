package com.example.eventease.model;

import com.example.eventease.testdata.TestDataHelper;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for Event model class.
 * Tests US 02.01.01 (Create event) and related event functionality.
 */
public class EventTest {
    private Event event;
    private String organizerId;

    @Before
    public void setUp() {
        organizerId = "organizer123";
        event = Event.newDraft(organizerId);
    }

    @Test
    public void testNewDraft_createsEventWithOrganizerId() {
        // US 02.01.01: Create event
        assertNotNull(event);
        assertEquals(organizerId, event.getOrganizerId());
        assertNotNull(event.getId());
        assertTrue(event.getId().length() > 0);
    }

    @Test
    public void testNewDraft_initializesWaitlistAndAdmitted() {
        // US 02.01.01: Event should have empty waitlist and admitted lists
        assertNotNull(event.getWaitlist());
        assertNotNull(event.getAdmitted());
        assertEquals(0, event.getWaitlist().size());
        assertEquals(0, event.getAdmitted().size());
    }

    @Test
    public void testEventSettersAndGetters() {
        // Test basic event properties
        event.setTitle("Test Event");
        event.setLocation("Test Location");
        event.setCapacity(50);
        event.setRegistrationStart(1000000L);
        event.setRegistrationEnd(2000000L);
        event.setDeadlineEpochMs(3000000L);
        event.setGuidelines("Test guidelines");
        event.setPosterUrl("https://example.com/poster.jpg");
        event.setQrPayload("event:test123");

        assertEquals("Test Event", event.getTitle());
        assertEquals("Test Location", event.getLocation());
        assertEquals(50, event.getCapacity());
        assertEquals(1000000L, event.getRegistrationStart());
        assertEquals(2000000L, event.getRegistrationEnd());
        assertEquals(3000000L, event.getDeadlineEpochMs());
        assertEquals("Test guidelines", event.getGuidelines());
        assertEquals("https://example.com/poster.jpg", event.getPosterUrl());
        assertEquals("event:test123", event.getQrPayload());
    }

    @Test
    public void testWaitlistManagement() {
        // US 01.01.01, US 01.01.02: Waitlist management
        List<String> waitlist = new ArrayList<>();
        waitlist.add("user1");
        waitlist.add("user2");
        
        event.setWaitlist(waitlist);
        
        assertEquals(2, event.getWaitlist().size());
        assertEquals(2, event.getWaitlistCount());
        assertTrue(event.getWaitlist().contains("user1"));
        assertTrue(event.getWaitlist().contains("user2"));
    }

    @Test
    public void testAdmittedListManagement() {
        // US 01.05.02: Track admitted entrants
        List<String> admitted = new ArrayList<>();
        admitted.add("user1");
        admitted.add("user2");
        
        event.setAdmitted(admitted);
        
        assertEquals(2, event.getAdmitted().size());
        assertTrue(event.getAdmitted().contains("user1"));
        assertTrue(event.getAdmitted().contains("user2"));
    }

    @Test
    public void testWaitlistCountSync() {
        // Waitlist count should sync with waitlist size
        List<String> waitlist = new ArrayList<>();
        waitlist.add("user1");
        waitlist.add("user2");
        waitlist.add("user3");
        
        event.setWaitlist(waitlist);
        
        assertEquals(3, event.getWaitlistCount());
    }

    @Test
    public void testToMap_convertsEventToMap() {
        // Test Firestore serialization
        event.setTitle("Test Event");
        event.setLocation("Test Location");
        event.setCapacity(50);
        event.setRegistrationStart(1000000L);
        event.setRegistrationEnd(2000000L);
        
        Map<String, Object> map = event.toMap();
        
        assertNotNull(map);
        assertEquals("Test Event", map.get("title"));
        assertEquals("Test Location", map.get("location"));
        assertEquals(50, map.get("capacity"));
        assertEquals(1000000L, map.get("registrationStart"));
        assertEquals(2000000L, map.get("registrationEnd"));
        assertEquals(organizerId, map.get("organizerId"));
    }

    @Test
    public void testFromMap_createsEventFromMap() {
        // Test Firestore deserialization
        Map<String, Object> map = new HashMap<>();
        map.put("id", "event123");
        map.put("title", "Test Event");
        map.put("location", "Test Location");
        map.put("capacity", 50);
        map.put("registrationStart", 1000000L);
        map.put("registrationEnd", 2000000L);
        map.put("organizerId", organizerId);
        map.put("waitlistCount", 0);
        map.put("waitlist", new ArrayList<>());
        map.put("admitted", new ArrayList<>());
        
        Event fromMap = Event.fromMap(map);
        
        assertNotNull(fromMap);
        assertEquals("event123", fromMap.getId());
        assertEquals("Test Event", fromMap.getTitle());
        assertEquals("Test Location", fromMap.getLocation());
        assertEquals(50, fromMap.getCapacity());
        assertEquals(1000000L, fromMap.getRegistrationStart());
        assertEquals(2000000L, fromMap.getRegistrationEnd());
    }

    @Test
    public void testFromMap_withNullMap() {
        // Test null safety
        Event fromMap = Event.fromMap(null);
        assertNull(fromMap);
    }

    @Test
    public void testGetStartAt_returnsDate() {
        long timestamp = System.currentTimeMillis() + 86400000; // Tomorrow
        event.setStartsAtEpochMs(timestamp);
        
        Date startDate = event.getStartAt();
        assertNotNull(startDate);
        assertEquals(timestamp, startDate.getTime());
    }

    @Test
    public void testGetStartAt_returnsNullForZero() {
        event.setStartsAtEpochMs(0);
        assertNull(event.getStartAt());
    }

    @Test
    public void testNotesAndDescription_interchangeability() {
        // Notes and description should be interchangeable
        event.setNotes("Test notes");
        assertEquals("Test notes", event.getDescription());
        
        event.setDescription("Test description");
        assertEquals("Test description", event.getDescription());
    }

    @Test
    public void testRealisticEvent_usingTestDataHelper() {
        // Test with realistic event data
        Event realisticEvent = TestDataHelper.createTestEvent(organizerId);
        
        assertNotNull(realisticEvent);
        assertEquals("Summer Music Festival 2025", realisticEvent.getTitle());
        assertEquals("Central Park, New York", realisticEvent.getLocation());
        assertEquals(500, realisticEvent.getCapacity());
        assertNotNull(realisticEvent.getGuidelines());
        assertNotNull(realisticEvent.getQrPayload());
        assertTrue(realisticEvent.getQrPayload().startsWith("event:"));
    }

    @Test
    public void testEventWithWaitlist_usingTestDataHelper() {
        // US 01.01.01, US 01.05.04: Test event with waitlist
        Event eventWithWaitlist = TestDataHelper.createTestEventWithWaitlist(organizerId, 10);
        
        assertNotNull(eventWithWaitlist.getWaitlist());
        assertEquals(10, eventWithWaitlist.getWaitlist().size());
        assertEquals(10, eventWithWaitlist.getWaitlistCount());
    }

    @Test
    public void testEventWithAdmitted_usingTestDataHelper() {
        // US 01.05.02: Test event with admitted entrants
        Event eventWithAdmitted = TestDataHelper.createTestEventWithAdmitted(organizerId, 5);
        
        assertNotNull(eventWithAdmitted.getAdmitted());
        assertEquals(5, eventWithAdmitted.getAdmitted().size());
    }
}


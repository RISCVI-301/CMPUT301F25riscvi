package com.example.eventease.data;

import com.example.eventease.model.Event;
import com.example.eventease.testdata.TestDataHelper;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unit tests for event details and QR code functionality.
 * Tests US 01.06.01 (View event details by scanning QR code), 
 * US 01.06.02 (Sign up for event from event details),
 * and US 01.05.05 (View lottery guidelines).
 * 
 * Uses an in-memory implementation to avoid dependencies on Firebase or organizer code.
 */
public class EventDetailsTest {
    
    private InMemoryEventDetailsRepository eventDetailsRepo;
    private InMemoryWaitlistRepository waitlistRepo;
    private String testEventId;
    private String testUserId;
    private String testOrganizerId;
    
    @Before
    public void setUp() {
        testOrganizerId = "organizer123";
        testEventId = "event123";
        testUserId = "user123";
        
        // Create event repository with test event
        InMemoryEventRepository eventRepo = new InMemoryEventRepository();
        Event testEvent = TestDataHelper.createTestEvent(testOrganizerId);
        testEvent.setId(testEventId);
        testEvent.setTitle("Swimming Lessons for Beginners");
        testEvent.setLocation("Community Pool");
        testEvent.setCapacity(20);
        testEvent.setGuidelines("Participants will be selected randomly. Must be 18+.");
        testEvent.setQrPayload("event:" + testEventId);
        long now = System.currentTimeMillis();
        testEvent.setRegistrationStart(now - 86400000L); // Started yesterday
        testEvent.setRegistrationEnd(now + 7 * 86400000L); // Ends in 7 days
        eventRepo.addEvent(testEvent);
        
        waitlistRepo = new InMemoryWaitlistRepository(eventRepo);
        eventDetailsRepo = new InMemoryEventDetailsRepository(eventRepo, waitlistRepo);
    }
    
    @Test
    public void testGetEventDetailsFromQRCode() throws Exception {
        // US 01.06.01: View event details within the app by scanning the promotional QR code
        String qrPayload = "event:" + testEventId;
        
        Task<Event> eventTask = eventDetailsRepo.getEventFromQRCode(qrPayload);
        Event event = Tasks.await(eventTask);
        
        assertTrue("Get event from QR code should succeed", eventTask.isSuccessful());
        assertNotNull("Event should not be null", event);
        assertEquals("Event ID should match", testEventId, event.getId());
        assertEquals("Event title should match", "Swimming Lessons for Beginners", event.getTitle());
        assertEquals("Event location should match", "Community Pool", event.getLocation());
    }
    
    @Test
    public void testGetEventDetailsFromQRCode_invalidFormat() throws Exception {
        // Try with invalid QR code format
        Task<Event> eventTask = eventDetailsRepo.getEventFromQRCode("invalid-format");
        Tasks.await(eventTask);
        
        assertFalse("Should fail for invalid QR code format", eventTask.isSuccessful());
        assertNotNull("Should have exception", eventTask.getException());
    }
    
    @Test
    public void testGetEventDetailsFromQRCode_nonexistentEvent() throws Exception {
        // Try with valid format but non-existent event
        Task<Event> eventTask = eventDetailsRepo.getEventFromQRCode("event:nonexistent");
        Tasks.await(eventTask);
        
        assertFalse("Should fail for non-existent event", eventTask.isSuccessful());
        assertNotNull("Should have exception", eventTask.getException());
    }
    
    @Test
    public void testJoinWaitlistFromEventDetails() throws Exception {
        // US 01.06.02: Be able to sign up for an event from the event details
        // Get event details first
        String qrPayload = "event:" + testEventId;
        Task<Event> eventTask = eventDetailsRepo.getEventFromQRCode(qrPayload);
        Event event = Tasks.await(eventTask);
        assertNotNull("Event should be retrieved", event);
        
        // Join waitlist from event details
        Task<Void> joinTask = eventDetailsRepo.joinWaitlistFromEventDetails(testEventId, testUserId);
        Tasks.await(joinTask);
        
        assertTrue("Join waitlist from event details should succeed", joinTask.isSuccessful());
        
        // Verify user is on waitlist
        Task<Boolean> isJoinedTask = waitlistRepo.isJoined(testEventId, testUserId);
        Boolean isJoined = Tasks.await(isJoinedTask);
        assertTrue("User should be on waitlist", isJoined);
    }
    
    @Test
    public void testViewEventGuidelines() throws Exception {
        // US 01.05.05: Be informed about the criteria or guidelines for the lottery selection process
        String qrPayload = "event:" + testEventId;
        
        Task<Event> eventTask = eventDetailsRepo.getEventFromQRCode(qrPayload);
        Event event = Tasks.await(eventTask);
        
        assertTrue("Get event should succeed", eventTask.isSuccessful());
        assertNotNull("Event should not be null", event);
        assertNotNull("Guidelines should not be null", event.getGuidelines());
        assertEquals("Guidelines should match", 
                     "Participants will be selected randomly. Must be 18+.", 
                     event.getGuidelines());
    }
    
    @Test
    public void testViewEventDetails_allFields() throws Exception {
        // Verify all event details are accessible
        String qrPayload = "event:" + testEventId;
        
        Task<Event> eventTask = eventDetailsRepo.getEventFromQRCode(qrPayload);
        Event event = Tasks.await(eventTask);
        
        assertTrue("Get event should succeed", eventTask.isSuccessful());
        assertNotNull("Event should not be null", event);
        
        // Verify all important fields are present
        assertEquals("Title should be accessible", "Swimming Lessons for Beginners", event.getTitle());
        assertEquals("Location should be accessible", "Community Pool", event.getLocation());
        assertEquals("Capacity should be accessible", 20, event.getCapacity());
        assertNotNull("Guidelines should be accessible", event.getGuidelines());
        assertNotNull("QR payload should be accessible", event.getQrPayload());
    }
    
    @Test
    public void testViewWaitlistCountFromEventDetails() throws Exception {
        // US 01.05.04: Know how many total entrants are on the waiting list
        // Add some users to waitlist
        Tasks.await(waitlistRepo.join(testEventId, "user1"));
        Tasks.await(waitlistRepo.join(testEventId, "user2"));
        Tasks.await(waitlistRepo.join(testEventId, "user3"));
        
        // Get event details
        String qrPayload = "event:" + testEventId;
        Task<Event> eventTask = eventDetailsRepo.getEventFromQRCode(qrPayload);
        Event event = Tasks.await(eventTask);
        
        assertTrue("Get event should succeed", eventTask.isSuccessful());
        assertNotNull("Event should not be null", event);
        assertEquals("Waitlist count should be accurate", 3, event.getWaitlistCount());
    }
    
    @Test
    public void testJoinWaitlistFromEventDetails_registrationClosed() throws Exception {
        // Create event with registration closed
        InMemoryEventRepository eventRepo = new InMemoryEventRepository();
        Event closedEvent = TestDataHelper.createTestEvent(testOrganizerId);
        closedEvent.setId("closedEvent");
        long now = System.currentTimeMillis();
        closedEvent.setRegistrationStart(now - 14 * 86400000L); // Started 14 days ago
        closedEvent.setRegistrationEnd(now - 7 * 86400000L); // Ended 7 days ago
        closedEvent.setQrPayload("event:closedEvent");
        eventRepo.addEvent(closedEvent);
        
        InMemoryWaitlistRepository closedWaitlistRepo = new InMemoryWaitlistRepository(eventRepo);
        InMemoryEventDetailsRepository closedEventDetailsRepo = 
            new InMemoryEventDetailsRepository(eventRepo, closedWaitlistRepo);
        
        // Try to join waitlist from event details when registration is closed
        Task<Void> joinTask = closedEventDetailsRepo.joinWaitlistFromEventDetails("closedEvent", testUserId);
        Tasks.await(joinTask);
        
        assertFalse("Join should fail when registration is closed", joinTask.isSuccessful());
        assertNotNull("Should have exception", joinTask.getException());
    }
    
    /**
     * In-memory implementation of EventDetailsRepository for testing.
     */
    private static class InMemoryEventDetailsRepository {
        private final InMemoryEventRepository eventRepo;
        private final InMemoryWaitlistRepository waitlistRepo;
        
        public InMemoryEventDetailsRepository(InMemoryEventRepository eventRepo, 
                                            InMemoryWaitlistRepository waitlistRepo) {
            this.eventRepo = eventRepo;
            this.waitlistRepo = waitlistRepo;
        }
        
        public Task<Event> getEventFromQRCode(String qrPayload) {
            // Parse QR code format: "event:<eventId>"
            if (qrPayload == null || !qrPayload.startsWith("event:")) {
                return Tasks.forException(new Exception("Invalid QR code format"));
            }
            
            String eventId = qrPayload.substring(6); // Remove "event:" prefix
            return eventRepo.getEvent(eventId);
        }
        
        public Task<Void> joinWaitlistFromEventDetails(String eventId, String uid) {
            return waitlistRepo.join(eventId, uid);
        }
    }
    
    /**
     * In-memory implementation of WaitlistRepository for testing.
     */
    private static class InMemoryWaitlistRepository implements WaitlistRepository {
        private final Map<String, Set<String>> waitlists = new ConcurrentHashMap<>();
        private final InMemoryEventRepository eventRepo;
        
        public InMemoryWaitlistRepository(InMemoryEventRepository eventRepo) {
            this.eventRepo = eventRepo;
        }
        
        @Override
        public Task<Void> join(String eventId, String uid) {
            return eventRepo.getEvent(eventId).continueWithTask(eventTask -> {
                if (!eventTask.isSuccessful() || eventTask.getResult() == null) {
                    return Tasks.forException(new Exception("Event not found"));
                }
                
                Event event = eventTask.getResult();
                
                // Check registration period
                long now = System.currentTimeMillis();
                if (event.getRegistrationStart() > 0 && now < event.getRegistrationStart()) {
                    return Tasks.forException(new Exception("Registration period has not started yet"));
                }
                
                if (event.getRegistrationEnd() > 0 && now > event.getRegistrationEnd()) {
                    return Tasks.forException(new Exception("Registration period has ended"));
                }
                
                // Add to waitlist
                waitlists.computeIfAbsent(eventId, k -> ConcurrentHashMap.newKeySet()).add(uid);
                event.getWaitlist().add(uid);
                event.setWaitlistCount(event.getWaitlist().size());
                
                return Tasks.forResult(null);
            });
        }
        
        @Override
        public Task<Boolean> isJoined(String eventId, String uid) {
            Set<String> waitlist = waitlists.get(eventId);
            return Tasks.forResult(waitlist != null && waitlist.contains(uid));
        }
        
        @Override
        public Task<Void> leave(String eventId, String uid) {
            Set<String> waitlist = waitlists.get(eventId);
            if (waitlist != null) {
                waitlist.remove(uid);
                eventRepo.getEvent(eventId).addOnSuccessListener(event -> {
                    if (event != null) {
                        event.getWaitlist().remove(uid);
                        event.setWaitlistCount(event.getWaitlist().size());
                    }
                });
            }
            return Tasks.forResult(null);
        }
    }
    
    /**
     * In-memory implementation of EventRepository for testing.
     */
    private static class InMemoryEventRepository {
        private final Map<String, Event> events = new ConcurrentHashMap<>();
        
        public void addEvent(Event event) {
            events.put(event.getId(), event);
        }
        
        public Task<Event> getEvent(String eventId) {
            Event event = events.get(eventId);
            if (event == null) {
                return Tasks.forException(new Exception("Event not found"));
            }
            return Tasks.forResult(event);
        }
        
    }
}


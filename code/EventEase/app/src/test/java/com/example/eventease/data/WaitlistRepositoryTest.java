package com.example.eventease.data;

import com.example.eventease.model.Event;
import com.example.eventease.testdata.TestDataHelper;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.example.eventease.TestTasks;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unit tests for WaitlistRepository interface.
 * Tests US 01.01.01 (Join waiting list) and US 01.01.02 (Leave waiting list).
 * 
 * Uses an in-memory implementation to avoid dependencies on Firebase or organizer code.
 */
public class WaitlistRepositoryTest {
    
    private InMemoryWaitlistRepository waitlistRepo;
    private InMemoryEventRepository eventRepo;
    private String testEventId;
    private String testUserId;
    private String testOrganizerId;
    
    @Before
    public void setUp() {
        testOrganizerId = "organizer123";
        testEventId = "event123";
        testUserId = "user123";
        
        // Create dummy event repository with test event
        eventRepo = new InMemoryEventRepository();
        Event testEvent = TestDataHelper.createTestEvent(testOrganizerId);
        testEvent.setId(testEventId);
        testEvent.setCapacity(50);
        long now = System.currentTimeMillis();
        testEvent.setRegistrationStart(now - 86400000L); // Started yesterday
        testEvent.setRegistrationEnd(now + 7 * 86400000L); // Ends in 7 days
        eventRepo.addEvent(testEvent);
        
        waitlistRepo = new InMemoryWaitlistRepository(eventRepo);
    }
    
    @Test
    public void testJoinWaitlist_success() throws Exception {
        // US 01.01.01: Join waiting list for a specific event
        Task<Void> joinTask = waitlistRepo.join(testEventId, testUserId);
        TestTasks.await(joinTask);
        
        assertTrue("Join task should succeed", joinTask.isSuccessful());
        
        // Verify user is on waitlist
        Task<Boolean> isJoinedTask = waitlistRepo.isJoined(testEventId, testUserId);
        Boolean isJoined = TestTasks.await(isJoinedTask);
        assertTrue("User should be on waitlist", isJoined);
    }
    
    @Test
    public void testJoinWaitlist_alreadyJoined() throws Exception {
        // Join once
        TestTasks.await(waitlistRepo.join(testEventId, testUserId));
        
        // Try to join again - should succeed (idempotent)
        Task<Void> joinAgainTask = waitlistRepo.join(testEventId, testUserId);
        TestTasks.await(joinAgainTask);
        assertTrue("Joining again should succeed (idempotent)", joinAgainTask.isSuccessful());
    }
    
    @Test
    public void testJoinWaitlist_registrationNotStarted() throws Exception {
        // Create event with registration not started yet
        Event futureEvent = TestDataHelper.createTestEvent(testOrganizerId);
        futureEvent.setId("futureEvent");
        long now = System.currentTimeMillis();
        futureEvent.setRegistrationStart(now + 86400000L); // Starts tomorrow
        futureEvent.setRegistrationEnd(now + 7 * 86400000L);
        eventRepo.addEvent(futureEvent);
        
        Task<Void> joinTask = waitlistRepo.join("futureEvent", testUserId);
        
        assertFalse("Join should fail when registration not started", joinTask.isSuccessful());
        assertNotNull("Should have exception", joinTask.getException());
    }
    
    @Test
    public void testJoinWaitlist_registrationEnded() throws Exception {
        // Create event with registration ended
        Event pastEvent = TestDataHelper.createTestEvent(testOrganizerId);
        pastEvent.setId("pastEvent");
        long now = System.currentTimeMillis();
        pastEvent.setRegistrationStart(now - 14 * 86400000L); // Started 14 days ago
        pastEvent.setRegistrationEnd(now - 7 * 86400000L); // Ended 7 days ago
        eventRepo.addEvent(pastEvent);
        
        Task<Void> joinTask = waitlistRepo.join("pastEvent", testUserId);
        
        assertFalse("Join should fail when registration ended", joinTask.isSuccessful());
        assertNotNull("Should have exception", joinTask.getException());
    }
    
    @Test
    public void testJoinWaitlist_capacityReached() throws Exception {
        // Create event with capacity of 2
        Event limitedEvent = TestDataHelper.createTestEvent(testOrganizerId);
        limitedEvent.setId("limitedEvent");
        limitedEvent.setCapacity(2);
        long now = System.currentTimeMillis();
        limitedEvent.setRegistrationStart(now - 86400000L);
        limitedEvent.setRegistrationEnd(now + 7 * 86400000L);
        eventRepo.addEvent(limitedEvent);
        
        // Fill capacity
        TestTasks.await(waitlistRepo.join("limitedEvent", "user1"));
        TestTasks.await(waitlistRepo.join("limitedEvent", "user2"));
        
        // Try to join when full
        Task<Void> joinTask = waitlistRepo.join("limitedEvent", testUserId);
        
        assertFalse("Join should fail when capacity reached", joinTask.isSuccessful());
        assertNotNull("Should have exception", joinTask.getException());
    }
    
    @Test
    public void testLeaveWaitlist_success() throws Exception {
        // US 01.01.02: Leave waiting list for a specific event
        // First join
        TestTasks.await(waitlistRepo.join(testEventId, testUserId));
        
        // Verify joined
        Boolean isJoined = TestTasks.await(waitlistRepo.isJoined(testEventId, testUserId));
        assertTrue("User should be on waitlist before leaving", isJoined);
        
        // Leave
        Task<Void> leaveTask = waitlistRepo.leave(testEventId, testUserId);
        TestTasks.await(leaveTask);
        
        assertTrue("Leave task should succeed", leaveTask.isSuccessful());
        
        // Verify user is no longer on waitlist
        Boolean stillJoined = TestTasks.await(waitlistRepo.isJoined(testEventId, testUserId));
        assertFalse("User should not be on waitlist after leaving", stillJoined);
    }
    
    @Test
    public void testLeaveWaitlist_notOnWaitlist() throws Exception {
        // Try to leave when not on waitlist - should succeed (idempotent)
        Task<Void> leaveTask = waitlistRepo.leave(testEventId, testUserId);
        TestTasks.await(leaveTask);
        
        // Should succeed even if not on waitlist (idempotent operation)
        assertTrue("Leave should succeed even if not on waitlist", leaveTask.isSuccessful());
    }
    
    @Test
    public void testIsJoined_notJoined() throws Exception {
        // Check if user is joined when they haven't joined
        Task<Boolean> isJoinedTask = waitlistRepo.isJoined(testEventId, testUserId);
        Boolean isJoined = TestTasks.await(isJoinedTask);
        
        assertFalse("User should not be on waitlist", isJoined);
    }
    
    @Test
    public void testIsJoined_afterJoin() throws Exception {
        // Join waitlist
        TestTasks.await(waitlistRepo.join(testEventId, testUserId));
        
        // Check if joined
        Task<Boolean> isJoinedTask = waitlistRepo.isJoined(testEventId, testUserId);
        Boolean isJoined = TestTasks.await(isJoinedTask);
        
        assertTrue("User should be on waitlist after joining", isJoined);
    }
    
    @Test
    public void testMultipleUsersJoinWaitlist() throws Exception {
        // Multiple users can join the same event
        String user1 = "user1";
        String user2 = "user2";
        String user3 = "user3";
        
        TestTasks.await(waitlistRepo.join(testEventId, user1));
        TestTasks.await(waitlistRepo.join(testEventId, user2));
        TestTasks.await(waitlistRepo.join(testEventId, user3));
        
        assertTrue("User1 should be on waitlist", TestTasks.await(waitlistRepo.isJoined(testEventId, user1)));
        assertTrue("User2 should be on waitlist", TestTasks.await(waitlistRepo.isJoined(testEventId, user2)));
        assertTrue("User3 should be on waitlist", TestTasks.await(waitlistRepo.isJoined(testEventId, user3)));
    }
    
    @Test
    public void testJoinWaitlist_eventNotFound() throws Exception {
        // Try to join non-existent event
        Task<Void> joinTask = waitlistRepo.join("nonexistent", testUserId);
        
        assertFalse("Join should fail for non-existent event", joinTask.isSuccessful());
        assertNotNull("Should have exception", joinTask.getException());
    }
    
    /**
     * In-memory implementation of WaitlistRepository for testing.
     * Does not depend on Firebase or organizer code.
     */
    private static class InMemoryWaitlistRepository implements WaitlistRepository {
        private final Map<String, Set<String>> waitlists = new ConcurrentHashMap<>();
        private final InMemoryEventRepository eventRepo;
        
        public InMemoryWaitlistRepository(InMemoryEventRepository eventRepo) {
            this.eventRepo = eventRepo;
        }
        
        @Override
        public Task<Void> join(String eventId, String uid) {
            Event event = eventRepo.getEventSync(eventId);
            if (event == null) {
                return Tasks.forException(new Exception("Event not found"));
            }
            
            if (event.getAdmitted().contains(uid)) {
                return Tasks.forException(new Exception("User is already admitted to this event"));
            }
            
            long now = System.currentTimeMillis();
            if (event.getRegistrationStart() > 0 && now < event.getRegistrationStart()) {
                return Tasks.forException(new Exception("Registration period has not started yet"));
            }
            
            if (event.getRegistrationEnd() > 0 && now > event.getRegistrationEnd()) {
                return Tasks.forException(new Exception("Registration period has ended"));
            }
            
            if (event.getCapacity() > 0) {
                Set<String> waitlist = waitlists.getOrDefault(eventId, new HashSet<>());
                if (waitlist.size() >= event.getCapacity()) {
                    return Tasks.forException(new Exception("Waitlist is full. Capacity reached."));
                }
            }
            
            waitlists.computeIfAbsent(eventId, k -> ConcurrentHashMap.newKeySet()).add(uid);
            event.getWaitlist().add(uid);
            event.setWaitlistCount(event.getWaitlist().size());
            
            return Tasks.forResult(null);
        }
        
        @Override
        public Task<Boolean> isJoined(String eventId, String uid) {
            Set<String> waitlist = waitlists.get(eventId);
            if (waitlist == null) {
                return Tasks.forResult(false);
            }
            return Tasks.forResult(waitlist.contains(uid));
        }
        
        @Override
        public Task<Void> leave(String eventId, String uid) {
            Set<String> waitlist = waitlists.get(eventId);
            if (waitlist != null) {
                waitlist.remove(uid);
                Event event = eventRepo.getEventSync(eventId);
                if (event != null) {
                    event.getWaitlist().remove(uid);
                    event.setWaitlistCount(event.getWaitlist().size());
                }
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

        public Event getEventSync(String eventId) {
            return events.get(eventId);
        }
    }
}


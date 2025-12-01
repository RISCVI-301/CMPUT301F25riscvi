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
 * Unit tests for EventRepository interface.
 * Tests US 01.01.03 (View list of events) and US 01.05.04 (View waitlist count).
 * 
 * Uses an in-memory implementation to avoid dependencies on Firebase or organizer code.
 */
public class EventRepositoryTest {
    
    private InMemoryEventRepository eventRepo;
    private String testOrganizerId;
    
    @Before
    public void setUp() {
        testOrganizerId = "organizer123";
        eventRepo = new InMemoryEventRepository();
    }
    
    @Test
    public void testGetOpenEvents_returnsOpenEvents() throws Exception {
        // US 01.01.03: View list of events that can join waiting list for
        long now = System.currentTimeMillis();
        
        // Create open event (registration open, hasn't started)
        Event openEvent = TestDataHelper.createTestEvent(testOrganizerId);
        openEvent.setId("openEvent");
        openEvent.setRegistrationStart(now - 86400000L); // Started yesterday
        openEvent.setRegistrationEnd(now + 7 * 86400000L); // Ends in 7 days
        openEvent.setStartsAtEpochMs(now + 30 * 86400000L); // Starts in 30 days
        eventRepo.addEvent(openEvent);
        
        // Create past event (already started)
        Event pastEvent = TestDataHelper.createTestEvent(testOrganizerId);
        pastEvent.setId("pastEvent");
        pastEvent.setRegistrationStart(now - 14 * 86400000L);
        pastEvent.setRegistrationEnd(now - 7 * 86400000L);
        pastEvent.setStartsAtEpochMs(now - 1 * 86400000L); // Started yesterday
        eventRepo.addEvent(pastEvent);
        
        // Create future event (hasn't started yet)
        Event futureEvent = TestDataHelper.createTestEvent(testOrganizerId);
        futureEvent.setId("futureEvent");
        futureEvent.setRegistrationStart(now - 86400000L);
        futureEvent.setRegistrationEnd(now + 7 * 86400000L);
        futureEvent.setStartsAtEpochMs(now + 60 * 86400000L); // Starts in 60 days
        eventRepo.addEvent(futureEvent);
        
        // Get open events
        Task<List<Event>> openEventsTask = eventRepo.getOpenEvents(new Date(now));
        List<Event> openEvents = TestTasks.await(openEventsTask);
        
        assertTrue("Task should succeed", openEventsTask.isSuccessful());
        assertNotNull("Open events list should not be null", openEvents);
        assertEquals("Should return 2 open events", 2, openEvents.size());
        
        // Verify past event is not included
        boolean hasPastEvent = openEvents.stream().anyMatch(e -> "pastEvent".equals(e.getId()));
        assertFalse("Past event should not be in open events", hasPastEvent);
        
        // Verify open and future events are included
        boolean hasOpenEvent = openEvents.stream().anyMatch(e -> "openEvent".equals(e.getId()));
        boolean hasFutureEvent = openEvents.stream().anyMatch(e -> "futureEvent".equals(e.getId()));
        assertTrue("Open event should be in results", hasOpenEvent);
        assertTrue("Future event should be in results", hasFutureEvent);
    }
    
    @Test
    public void testGetOpenEvents_emptyWhenNoEvents() throws Exception {
        // Get open events when no events exist
        Task<List<Event>> openEventsTask = eventRepo.getOpenEvents(new Date());
        List<Event> openEvents = TestTasks.await(openEventsTask);
        
        assertTrue("Task should succeed", openEventsTask.isSuccessful());
        assertNotNull("Open events list should not be null", openEvents);
        assertTrue("Should return empty list when no events", openEvents.isEmpty());
    }
    
    @Test
    public void testGetEvent_success() throws Exception {
        // Create and add event
        Event testEvent = TestDataHelper.createTestEvent(testOrganizerId);
        testEvent.setId("testEvent");
        testEvent.setTitle("Test Event Title");
        eventRepo.addEvent(testEvent);
        
        // Get event by ID
        Task<Event> eventTask = eventRepo.getEvent("testEvent");
        Event retrievedEvent = TestTasks.await(eventTask);
        
        assertTrue("Task should succeed", eventTask.isSuccessful());
        assertNotNull("Event should not be null", retrievedEvent);
        assertEquals("Event ID should match", "testEvent", retrievedEvent.getId());
        assertEquals("Event title should match", "Test Event Title", retrievedEvent.getTitle());
    }
    
    @Test
    public void testGetEvent_notFound() throws Exception {
        // Try to get non-existent event
        Task<Event> eventTask = eventRepo.getEvent("nonexistent");
        
        assertFalse("Task should fail for non-existent event", eventTask.isSuccessful());
        assertNotNull("Should have exception", eventTask.getException());
    }
    
    @Test
    public void testGetWaitlistCount() throws Exception {
        // US 01.05.04: Know how many total entrants are on the waiting list
        Event testEvent = TestDataHelper.createTestEvent(testOrganizerId);
        testEvent.setId("testEvent");
        testEvent.setWaitlistCount(15);
        eventRepo.addEvent(testEvent);
        
        // Get event and check waitlist count
        Task<Event> eventTask = eventRepo.getEvent("testEvent");
        Event event = TestTasks.await(eventTask);
        
        assertTrue("Task should succeed", eventTask.isSuccessful());
        assertNotNull("Event should not be null", event);
        assertEquals("Waitlist count should match", 15, event.getWaitlistCount());
    }
    
    @Test
    public void testGetWaitlistCount_updatesWhenWaitlistChanges() throws Exception {
        // Create event with waitlist
        Event testEvent = TestDataHelper.createTestEvent(testOrganizerId);
        testEvent.setId("testEvent");
        List<String> waitlist = Arrays.asList("user1", "user2", "user3");
        testEvent.setWaitlist(waitlist);
        eventRepo.addEvent(testEvent);
        
        // Get event and verify waitlist count
        Task<Event> eventTask = eventRepo.getEvent("testEvent");
        Event event = TestTasks.await(eventTask);
        
        assertTrue("Task should succeed", eventTask.isSuccessful());
        assertEquals("Waitlist count should match waitlist size", 3, event.getWaitlistCount());
        assertEquals("Waitlist count should match waitlist size", 3, event.getWaitlist().size());
    }
    
    @Test
    public void testGetOpenEvents_sortedByStartTime() throws Exception {
        // Create events with different start times
        long now = System.currentTimeMillis();
        
        Event event1 = TestDataHelper.createTestEvent(testOrganizerId);
        event1.setId("event1");
        event1.setStartsAtEpochMs(now + 30 * 86400000L); // Starts in 30 days
        event1.setRegistrationStart(now - 86400000L);
        event1.setRegistrationEnd(now + 7 * 86400000L);
        eventRepo.addEvent(event1);
        
        Event event2 = TestDataHelper.createTestEvent(testOrganizerId);
        event2.setId("event2");
        event2.setStartsAtEpochMs(now + 10 * 86400000L); // Starts in 10 days (earlier)
        event2.setRegistrationStart(now - 86400000L);
        event2.setRegistrationEnd(now + 7 * 86400000L);
        eventRepo.addEvent(event2);
        
        Event event3 = TestDataHelper.createTestEvent(testOrganizerId);
        event3.setId("event3");
        event3.setStartsAtEpochMs(now + 20 * 86400000L); // Starts in 20 days
        event3.setRegistrationStart(now - 86400000L);
        event3.setRegistrationEnd(now + 7 * 86400000L);
        eventRepo.addEvent(event3);
        
        // Get open events
        Task<List<Event>> openEventsTask = eventRepo.getOpenEvents(new Date(now));
        List<Event> openEvents = TestTasks.await(openEventsTask);
        
        assertTrue("Task should succeed", openEventsTask.isSuccessful());
        assertEquals("Should return 3 events", 3, openEvents.size());
        
        // Verify sorted by start time (earliest first)
        assertEquals("First event should be event2 (earliest)", "event2", openEvents.get(0).getId());
        assertEquals("Second event should be event3", "event3", openEvents.get(1).getId());
        assertEquals("Third event should be event1 (latest)", "event1", openEvents.get(2).getId());
    }
    
    @Test
    public void testEventWithGuidelines() throws Exception {
        // US 01.05.05: Know about criteria or guidelines for lottery selection
        Event testEvent = TestDataHelper.createTestEvent(testOrganizerId);
        testEvent.setId("testEvent");
        testEvent.setGuidelines("Participants will be selected randomly. Must be 18+.");
        eventRepo.addEvent(testEvent);
        
        // Get event and check guidelines
        Task<Event> eventTask = eventRepo.getEvent("testEvent");
        Event event = TestTasks.await(eventTask);
        
        assertTrue("Task should succeed", eventTask.isSuccessful());
        assertNotNull("Event should not be null", event);
        assertNotNull("Guidelines should not be null", event.getGuidelines());
        assertEquals("Guidelines should match", 
                     "Participants will be selected randomly. Must be 18+.", 
                     event.getGuidelines());
    }
    
    /**
     * In-memory implementation of EventRepository for testing.
     */
    private static class InMemoryEventRepository implements EventRepository {
        private final Map<String, Event> events = new ConcurrentHashMap<>();
        
        public void addEvent(Event event) {
            events.put(event.getId(), event);
        }
        
        @Override
        public Task<List<Event>> getOpenEvents(Date now) {
            List<Event> openEvents = new ArrayList<>();
            long nowMs = now.getTime();
            
            for (Event event : events.values()) {
                // Event is open if it hasn't started yet
                if (event.getStartsAtEpochMs() == 0 || event.getStartsAtEpochMs() > nowMs) {
                    openEvents.add(event);
                }
            }
            
            // Sort by start time (earliest first)
            openEvents.sort(Comparator.comparing(
                Event::getStartsAtEpochMs, 
                Comparator.nullsLast(Comparator.naturalOrder())
            ));
            
            return Tasks.forResult(Collections.unmodifiableList(openEvents));
        }
        
        @Override
        public Task<Event> getEvent(String eventId) {
            Event event = events.get(eventId);
            if (event == null) {
                return Tasks.forException(new Exception("Event not found"));
            }
            return Tasks.forResult(event);
        }
        
        @Override
        public ListenerRegistration listenWaitlistCount(String eventId, WaitlistCountListener l) {
            // Not needed for these tests
            return new ListenerRegistration() {
                @Override
                public void remove() {
                    // No-op
                }
            };
        }
        
        @Override
        public Task<Void> create(Event event) {
            events.put(event.getId(), event);
            return Tasks.forResult(null);
        }
    }
}


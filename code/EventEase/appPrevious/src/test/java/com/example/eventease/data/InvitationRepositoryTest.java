package com.example.eventease.data;

import com.example.eventease.model.Event;
import com.example.eventease.model.Invitation;
import com.example.eventease.testdata.TestDataHelper;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unit tests for InvitationRepository interface.
 * Tests US 01.05.02 (Accept invitation) and US 01.05.03 (Decline invitation).
 * 
 * Uses an in-memory implementation to avoid dependencies on Firebase or organizer code.
 */
public class InvitationRepositoryTest {
    
    private InMemoryInvitationRepository invitationRepo;
    private InMemoryAdmittedRepository admittedRepo;
    private String testEventId;
    private String testUserId;
    private String testInvitationId;
    
    @Before
    public void setUp() {
        testEventId = "event123";
        testUserId = "user123";
        testInvitationId = "inv123";
        
        admittedRepo = new InMemoryAdmittedRepository();
        invitationRepo = new InMemoryInvitationRepository(admittedRepo);
    }
    
    @Test
    public void testAcceptInvitation_success() throws Exception {
        // US 01.05.02: Accept invitation to register/sign up when chosen
        // Create invitation
        Invitation invitation = TestDataHelper.createTestInvitation(testInvitationId, testEventId, testUserId);
        invitationRepo.addInvitation(invitation);
        
        // Accept invitation
        Task<Void> acceptTask = invitationRepo.accept(testInvitationId, testEventId, testUserId);
        Tasks.await(acceptTask);
        
        assertTrue("Accept task should succeed", acceptTask.isSuccessful());
        
        // Verify invitation status is ACCEPTED
        Invitation updatedInvitation = invitationRepo.getInvitation(testInvitationId);
        assertNotNull("Invitation should exist", updatedInvitation);
        assertEquals("Invitation status should be ACCEPTED", 
                     Invitation.Status.ACCEPTED, updatedInvitation.getStatus());
        
        // Verify user is admitted
        Task<Boolean> isAdmittedTask = admittedRepo.isAdmitted(testEventId, testUserId);
        Boolean isAdmitted = Tasks.await(isAdmittedTask);
        assertTrue("User should be admitted after accepting invitation", isAdmitted);
    }
    
    @Test
    public void testDeclineInvitation_success() throws Exception {
        // US 01.05.03: Decline invitation when chosen to participate
        // Create invitation
        Invitation invitation = TestDataHelper.createTestInvitation(testInvitationId, testEventId, testUserId);
        invitationRepo.addInvitation(invitation);
        
        // Decline invitation
        Task<Void> declineTask = invitationRepo.decline(testInvitationId, testEventId, testUserId);
        Tasks.await(declineTask);
        
        assertTrue("Decline task should succeed", declineTask.isSuccessful());
        
        // Verify invitation status is DECLINED
        Invitation updatedInvitation = invitationRepo.getInvitation(testInvitationId);
        assertNotNull("Invitation should exist", updatedInvitation);
        assertEquals("Invitation status should be DECLINED", 
                     Invitation.Status.DECLINED, updatedInvitation.getStatus());
        
        // Verify user is NOT admitted
        Task<Boolean> isAdmittedTask = admittedRepo.isAdmitted(testEventId, testUserId);
        Boolean isAdmitted = Tasks.await(isAdmittedTask);
        assertFalse("User should not be admitted after declining invitation", isAdmitted);
    }
    
    @Test
    public void testAcceptInvitation_notFound() throws Exception {
        // Try to accept non-existent invitation
        Task<Void> acceptTask = invitationRepo.accept("nonexistent", testEventId, testUserId);
        Tasks.await(acceptTask);
        
        assertFalse("Accept should fail for non-existent invitation", acceptTask.isSuccessful());
        assertNotNull("Should have exception", acceptTask.getException());
    }
    
    @Test
    public void testDeclineInvitation_notFound() throws Exception {
        // Try to decline non-existent invitation
        Task<Void> declineTask = invitationRepo.decline("nonexistent", testEventId, testUserId);
        Tasks.await(declineTask);
        
        assertFalse("Decline should fail for non-existent invitation", declineTask.isSuccessful());
        assertNotNull("Should have exception", declineTask.getException());
    }
    
    @Test
    public void testListenActiveInvitations() throws Exception {
        // Create multiple invitations for the user
        Invitation inv1 = TestDataHelper.createTestInvitation("inv1", testEventId, testUserId);
        Invitation inv2 = TestDataHelper.createTestInvitation("inv2", "event2", testUserId);
        Invitation inv3 = TestDataHelper.createTestInvitation("inv3", "event3", "otherUser");
        
        invitationRepo.addInvitation(inv1);
        invitationRepo.addInvitation(inv2);
        invitationRepo.addInvitation(inv3);
        
        // Listen for active invitations
        List<Invitation> receivedInvitations = new ArrayList<>();
        InvitationListener listener = receivedInvitations::addAll;
        
        ListenerRegistration registration = invitationRepo.listenActive(testUserId, listener);
        
        // Wait a bit for listener to be called
        Thread.sleep(100);
        
        // Should receive invitations for testUserId only
        assertEquals("Should receive 2 invitations for test user", 2, receivedInvitations.size());
        assertTrue("Should contain inv1", receivedInvitations.stream()
            .anyMatch(inv -> "inv1".equals(inv.getId())));
        assertTrue("Should contain inv2", receivedInvitations.stream()
            .anyMatch(inv -> "inv2".equals(inv.getId())));
        
        // Clean up
        registration.remove();
    }
    
    @Test
    public void testAcceptInvitation_removesFromActive() throws Exception {
        // Create invitation
        Invitation invitation = TestDataHelper.createTestInvitation(testInvitationId, testEventId, testUserId);
        invitationRepo.addInvitation(invitation);
        
        // Listen for active invitations
        List<Invitation> receivedInvitations = new ArrayList<>();
        InvitationListener listener = receivedInvitations::addAll;
        ListenerRegistration registration = invitationRepo.listenActive(testUserId, listener);
        
        Thread.sleep(50);
        int initialCount = receivedInvitations.size();
        receivedInvitations.clear();
        
        // Accept invitation
        Tasks.await(invitationRepo.accept(testInvitationId, testEventId, testUserId));
        
        Thread.sleep(50);
        
        // Should not receive the accepted invitation anymore
        assertTrue("Accepted invitation should be removed from active list", 
                  receivedInvitations.isEmpty() || 
                  receivedInvitations.stream().noneMatch(inv -> testInvitationId.equals(inv.getId())));
        
        registration.remove();
    }
    
    @Test
    public void testExpiredInvitation_notInActive() throws Exception {
        // Create expired invitation
        Invitation expiredInv = new Invitation();
        expiredInv.setId("expiredInv");
        expiredInv.setEventId(testEventId);
        expiredInv.setUid(testUserId);
        expiredInv.setStatus(Invitation.Status.PENDING);
        expiredInv.setIssuedAt(new Date(System.currentTimeMillis() - 10 * 24 * 60 * 60 * 1000L)); // 10 days ago
        expiredInv.setExpiresAt(new Date(System.currentTimeMillis() - 1 * 24 * 60 * 60 * 1000L)); // Expired yesterday
        invitationRepo.addInvitation(expiredInv);
        
        // Listen for active invitations
        List<Invitation> receivedInvitations = new ArrayList<>();
        InvitationListener listener = receivedInvitations::addAll;
        ListenerRegistration registration = invitationRepo.listenActive(testUserId, listener);
        
        Thread.sleep(50);
        
        // Should not receive expired invitation
        assertTrue("Expired invitation should not be in active list",
                  receivedInvitations.stream().noneMatch(inv -> "expiredInv".equals(inv.getId())));
        
        registration.remove();
    }
    
    /**
     * In-memory implementation of InvitationRepository for testing.
     */
    private static class InMemoryInvitationRepository implements InvitationRepository {
        private final Map<String, Invitation> invitations = new ConcurrentHashMap<>();
        private final Map<String, List<InvitationListener>> listeners = new ConcurrentHashMap<>();
        private final InMemoryAdmittedRepository admittedRepo;
        
        public InMemoryInvitationRepository(InMemoryAdmittedRepository admittedRepo) {
            this.admittedRepo = admittedRepo;
        }
        
        public void addInvitation(Invitation invitation) {
            invitations.put(invitation.getId(), invitation);
            notifyListeners(invitation.getUid());
        }
        
        public Invitation getInvitation(String invitationId) {
            return invitations.get(invitationId);
        }
        
        @Override
        public ListenerRegistration listenActive(String uid, InvitationListener l) {
            listeners.computeIfAbsent(uid, k -> new ArrayList<>()).add(l);
            
            // Immediately notify with current active invitations
            notifyListeners(uid);
            
            return new ListenerRegistration() {
                @Override
                public void remove() {
                    List<InvitationListener> ls = listeners.get(uid);
                    if (ls != null) {
                        ls.remove(l);
                    }
                }
            };
        }
        
        @Override
        public Task<Void> accept(String invitationId, String eventId, String uid) {
            Invitation inv = invitations.get(invitationId);
            if (inv == null) {
                return Tasks.forException(new Exception("Invitation not found"));
            }
            
            inv.setStatus(Invitation.Status.ACCEPTED);
            
            // Admit user to event
            return admittedRepo.admit(eventId, uid).continueWithTask(task -> {
                notifyListeners(uid);
                return task.isSuccessful() ? Tasks.forResult(null) : Tasks.forException(task.getException());
            });
        }
        
        @Override
        public Task<Void> decline(String invitationId, String eventId, String uid) {
            Invitation inv = invitations.get(invitationId);
            if (inv == null) {
                return Tasks.forException(new Exception("Invitation not found"));
            }
            
            inv.setStatus(Invitation.Status.DECLINED);
            notifyListeners(uid);
            return Tasks.forResult(null);
        }
        
        private void notifyListeners(String uid) {
            List<InvitationListener> ls = listeners.get(uid);
            if (ls != null) {
                List<Invitation> active = getActiveInvitations(uid);
                for (InvitationListener l : new ArrayList<>(ls)) {
                    l.onChanged(active);
                }
            }
        }
        
        private List<Invitation> getActiveInvitations(String uid) {
            List<Invitation> active = new ArrayList<>();
            Date now = new Date();
            for (Invitation inv : invitations.values()) {
                if (uid.equals(inv.getUid()) && 
                    inv.getStatus() == Invitation.Status.PENDING &&
                    (inv.getExpiresAt() == null || inv.getExpiresAt().after(now))) {
                    active.add(inv);
                }
            }
            return active;
        }
    }
    
    /**
     * In-memory implementation of AdmittedRepository for testing.
     */
    private static class InMemoryAdmittedRepository implements AdmittedRepository {
        private final Map<String, Set<String>> admittedUsers = new ConcurrentHashMap<>();
        
        @Override
        public Task<Void> admit(String eventId, String uid) {
            admittedUsers.computeIfAbsent(eventId, k -> ConcurrentHashMap.newKeySet()).add(uid);
            return Tasks.forResult(null);
        }
        
        @Override
        public Task<Boolean> isAdmitted(String eventId, String uid) {
            Set<String> admitted = admittedUsers.get(eventId);
            return Tasks.forResult(admitted != null && admitted.contains(uid));
        }
        
        @Override
        public Task<List<Event>> getUpcomingEvents(String uid) {
            // Not needed for these tests
            return Tasks.forResult(new ArrayList<>());
        }
        
        @Override
        public Task<List<Event>> getPreviousEvents(String uid) {
            // Not needed for these tests
            return Tasks.forResult(new ArrayList<>());
        }
    }
}


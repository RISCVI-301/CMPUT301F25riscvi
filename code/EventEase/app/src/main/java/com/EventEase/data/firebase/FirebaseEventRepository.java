package com.EventEase.data.firebase;

import com.EventEase.data.EventRepository;
import com.EventEase.data.ListenerRegistration;
import com.EventEase.data.WaitlistCountListener;
import com.EventEase.model.Event;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FirebaseEventRepository
 * Prod name. Currently uses in-memory storage for Step 1 contracts.
 * TODO(salaar): Replace in-memory store with Firebase (Firestore/RTDB).
 */
public class FirebaseEventRepository implements EventRepository {

    private final Map<String, Event> events = new ConcurrentHashMap<>();
    private final Map<String, Integer> waitlistCounts = new ConcurrentHashMap<>();
    private final Map<String, List<WaitlistCountListener>> listeners = new ConcurrentHashMap<>();

    public FirebaseEventRepository(List<Event> seed) {
        for (Event e : seed) {
            events.put(e.getId(), e);
            waitlistCounts.put(e.getId(), 0);
        }
    }

    @Override
    public Task<List<Event>> getOpenEvents(Date now) {
        List<Event> result = new ArrayList<>();
        for (Event e : events.values()) {
            if (e.getStartAt() == null || e.getStartAt().after(now)) {
                result.add(e);
            }
        }
        result.sort(Comparator.comparing(Event::getStartAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return Tasks.forResult(Collections.unmodifiableList(result));
    }

    @Override
    public Task<Event> getEvent(String eventId) {
        Event e = events.get(eventId);
        if (e != null) return Tasks.forResult(e);
        return Tasks.forException(new NoSuchElementException("Event not found: " + eventId));
    }

    @Override
    public ListenerRegistration listenWaitlistCount(String eventId, WaitlistCountListener l) {
        listeners.computeIfAbsent(eventId, k -> new ArrayList<>()).add(l);
        l.onChanged(waitlistCounts.getOrDefault(eventId, 0));
        return new ListenerRegistration() {
            private boolean removed = false;
            @Override public void remove() {
                if (removed) return;
                List<WaitlistCountListener> ls = listeners.get(eventId);
                if (ls != null) ls.remove(l);
                removed = true;
            }
        };
    }

    /* package */ void incrementWaitlist(String eventId) {
        waitlistCounts.merge(eventId, 1, Integer::sum);
        notifyCount(eventId);
    }

    /* package */ boolean isKnown(String eventId) {
        return events.containsKey(eventId);
    }

    private void notifyCount(String eventId) {
        int count = waitlistCounts.getOrDefault(eventId, 0);
        List<WaitlistCountListener> ls = listeners.get(eventId);
        if (ls != null) {
            for (WaitlistCountListener l : new ArrayList<>(ls)) {
                l.onChanged(count);
            }
        }
    }
}

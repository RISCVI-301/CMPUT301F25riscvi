package com.example.eventease;

import com.google.android.gms.tasks.Task;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility for awaiting Google Tasks from JVM tests without triggering the
 * "Must not be called on the main application thread" exception. Uses a
 * CountDownLatch and direct executor to wait for task completion without
 * relying on Google Play Services' {@link com.google.android.gms.tasks.Tasks#await}.
 */
public final class TestTasks {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final long DEFAULT_TIMEOUT_MS = 5000L;

    private TestTasks() {
        // no instances
    }

    /**
     * Awaits completion of the provided Task on a background thread.
     *
     * @throws Exception if the task fails or waiting is interrupted
     */
    public static <T> T await(Task<T> task) throws Exception {
        return await(task, DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Awaits completion with timeout.
     */
    public static <T> T await(Task<T> task, long timeout, TimeUnit unit) throws Exception {
        if (task.isComplete()) {
            return getResult(task);
        }

        CountDownLatch latch = new CountDownLatch(1);
        task.addOnCompleteListener(DIRECT_EXECUTOR, completed -> latch.countDown());

        if (!latch.await(timeout, unit)) {
            throw new TimeoutException("Task did not complete within " + timeout + " " + unit);
        }

        if (!task.isComplete()) {
            throw new TimeoutException("Task did not complete after latch countdown");
        }

        return getResult(task);
    }

    private static <T> T getResult(Task<T> task) throws Exception {
        if (task.isSuccessful()) {
            return task.getResult();
        }
        Throwable cause = task.getException();
        if (cause instanceof Exception) {
            throw (Exception) cause;
        }
        throw new RuntimeException(cause);
    }
}


package de.tum.cit.aet.util;

import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.IntConsumer;

/**
 * Runs the per-student work of a simulation concurrently.
 * <p>
 * Everything a simulated student does is blocking I/O: REST calls to Artemis and git clones and pushes over HTTPS. The
 * work is spent waiting for a server, not computing, so the number of tasks that can usefully be in flight has nothing
 * to do with how many cores the tool host has. Sizing a pool of platform threads from the core count, as this code used
 * to, capped a run far below what either machine could sustain: at 1000 students the tool host sat at 26% CPU while its
 * 80 threads were 87% busy, so the measurement described the load generator rather than the server under test.
 * <p>
 * Virtual threads remove that ceiling. A student who is waiting for a response parks their thread and releases the
 * carrier, so a run can hold as many students in flight as it claims to simulate. This needs Java 24 or later to be
 * worthwhile, since before <a href="https://openjdk.org/jeps/491">JEP 491</a> a {@code synchronized} block pinned a
 * virtual thread to its carrier, and JGit takes monitors on paths a clone goes through.
 * <p>
 * One caveat survives the change. Virtual threads only help where the blocking operation is a socket. File I/O still
 * occupies the carrier for its duration, and a clone writes objects to disk, so a slow working directory limits a run
 * no matter how high the concurrency goes. Keep the repositories the students clone into on fast storage.
 */
public final class SimulationConcurrency {

    /**
     * Upper bound on students in flight when nothing else is configured.
     * <p>
     * A limit exists so that pointing the tool at an unfamiliar deployment cannot turn a measurement into a denial of
     * service, and so that an operator has something to turn down when looking for the point at which a system bends.
     * It is set well above the largest rung of the documented ladder, so in practice a run is limited by its own
     * student count rather than by this number.
     */
    public static final int DEFAULT_MAX_CONCURRENCY = 2000;

    private SimulationConcurrency() {}

    /**
     * Decide how many students may be in flight at once.
     *
     * @param numberOfUsers  the number of students the run simulates
     * @param maxConcurrency the configured ceiling
     * @return the number of students to run concurrently, at least one
     */
    public static int concurrencyFor(int numberOfUsers, int maxConcurrency) {
        return Math.max(1, Math.min(numberOfUsers, maxConcurrency));
    }

    /**
     * Decide how many students may be in flight at once, using the default ceiling.
     *
     * @param numberOfUsers the number of students the run simulates
     * @return the number of students to run concurrently, at least one
     */
    public static int concurrencyFor(int numberOfUsers) {
        return concurrencyFor(numberOfUsers, DEFAULT_MAX_CONCURRENCY);
    }

    /**
     * Apply an action to every index from zero to {@code count}, running up to {@code concurrency} of them at a time,
     * and return once all of them have finished.
     * <p>
     * Each index gets its own virtual thread. Threads beyond the concurrency limit park on a semaphore until a slot
     * frees up, which costs a few kilobytes each rather than an operating system thread.
     * <p>
     * An action that throws takes its own index down and nothing else, matching how a single student failing has always
     * been treated: the run continues and the remaining students still produce measurements. Callers that want the
     * failure recorded should catch inside the action, since the exception is not visible here.
     *
     * @param concurrency how many actions may run at the same time
     * @param count       the number of indices to cover
     * @param action      the work to perform for one index
     */
    public static void forEachIndex(int concurrency, int count, IntConsumer action) {
        Semaphore permits = new Semaphore(concurrency);

        // close() waits for every submitted task to finish, so the method returns only once the whole batch is done.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < count; index++) {
                int currentIndex = index;
                executor.submit(() -> {
                    // Acquired outside the try so that an interrupt while waiting does not release a permit we never
                    // took.
                    permits.acquire();
                    try {
                        action.accept(currentIndex);
                    } finally {
                        permits.release();
                    }
                    return null;
                });
            }
        }
    }
}

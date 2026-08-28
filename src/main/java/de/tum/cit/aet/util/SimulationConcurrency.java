package de.tum.cit.aet.util;

import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
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
 * Virtual threads decouple that ceiling from the core count. A student who is waiting for a response parks their
 * thread and releases the carrier, so how many students are in flight becomes a deliberate setting rather than an
 * accident of the host's hardware. This needs Java 24 or later to be
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
     * Deliberately a modest step up from the 80 that the old core-count formula produced on an eight core host, rather
     * than the thousands virtual threads would technically allow. Two reasons to stay conservative. The tool host was
     * already working hard at 80: its disk ran 82% busy during a 1000-student run, and disk is the one thing virtual
     * threads do not help with, because file I/O holds its carrier thread for the duration. And a tool that defaults to
     * thousands of simultaneous students is a denial of service aimed at whatever it is pointed at.
     * <p>
     * So this is a ceiling to raise deliberately, having looked at the tool host's own CPU, memory and disk, rather
     * than a number to leave alone. Unlike the formula it replaces it is at least visible and adjustable, through
     * {@code benchmarking.simulation.max-concurrency}. A run with more students than this still simulates every one of
     * them and queues the excess, exactly as it did before.
     */
    public static final int DEFAULT_MAX_CONCURRENCY = 200;

    /**
     * Lower bound of the pause a simulated user takes between two actions.
     * <p>
     * Five seconds rather than the two it used to be. A student in an exam reads a question, thinks, and types; they do
     * not fire a request every couple of seconds for the length of the exam. The shorter pause made a run heavier than
     * any real cohort and compressed a two-hour exam into a burst.
     */
    public static final long DEFAULT_MIN_THINK_TIME_MILLIS = 5000;

    /** Upper bound of the pause a simulated user takes between two actions. */
    public static final long DEFAULT_MAX_THINK_TIME_MILLIS = 10_000;

    /**
     * Window across which students enter a phase, rather than all on the same tick.
     * <p>
     * Every phase is a barrier: nobody starts the exam until everyone has finished the initial calls. That resets any
     * spread the previous phase built up, so each phase begins with the whole cohort in lockstep and the first thing
     * it does arrives as one spike. A real cohort is never that aligned — students reach a view seconds apart even
     * when they started together.
     * <p>
     * Costs at most this much wall clock per phase, since the last student to arrive still does the same work.
     * <p>
     * Not applied unless a caller passes it: only a simulation run wants its workers to start at different times.
     */
    public static final long DEFAULT_ARRIVAL_SPREAD_MILLIS = 15_000;

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
        forEachIndex(concurrency, count, (index, thinkTime) -> action.accept(index));
    }

    /**
     * Think time a simulated user spends between two actions.
     * <p>
     * Real students do not fire their next request the instant the previous one returns, and a benchmark that does
     * produces a load shape no exam ever has: brief spikes an order of magnitude above the average, separated by idle
     * seconds. Pausing between actions spreads the same amount of work over the run.
     */
    @FunctionalInterface
    public interface ThinkTime {
        /** Does nothing, for callers that want the actions back to back. */
        ThinkTime NONE = () -> {};

        /**
         * Pauses before the next action.
         */
        void pause();
    }

    /**
     * Applies an action to every index, as {@link #forEachIndex(int, int, IntConsumer)} does, and additionally hands each
     * action a {@link ThinkTime} to call between its own steps.
     * <p>
     * The pause deliberately gives up the concurrency permit while it waits and takes it again afterwards. The permit
     * exists to cap how many users are talking to the server at once, and a user who is thinking is not one of them;
     * holding it would turn think time into a throughput limit rather than a pacing device.
     *
     * @param concurrency how many actions may run at the same time
     * @param count       the number of indices to cover
     * @param action      the work to perform for one index
     */
    public static void forEachIndex(int concurrency, int count, PausableAction action) {
        forEachIndex(concurrency, count, DEFAULT_MIN_THINK_TIME_MILLIS, DEFAULT_MAX_THINK_TIME_MILLIS, action);
    }

    /**
     * As {@link #forEachIndex(int, int, PausableAction)}, with the think time range stated explicitly.
     * <p>
     * Public so a run can pace its users from configuration, and so tests can pace them in milliseconds and still
     * finish in reasonable time.
     *
     * @param concurrency         how many actions may run at the same time
     * @param count               the number of indices to cover
     * @param minThinkTimeMillis  lower end of the intended pause; the mean of the two bounds is the mean pause
     * @param maxThinkTimeMillis  upper end of the intended pause; individual pauses may exceed it, see
     *                            {@link #nextThinkTimeMillis(long, long)}
     * @param action              the work to perform for one index
     */
    public static void forEachIndex(int concurrency, int count, long minThinkTimeMillis, long maxThinkTimeMillis, PausableAction action) {
        // No arrival spread unless a caller asks for one. Spreading arrivals is a property of a benchmark run, not of
        // running work concurrently, and a utility that silently delayed its callers would be a poor one.
        forEachIndex(concurrency, count, minThinkTimeMillis, maxThinkTimeMillis, 0, action);
    }

    /**
     * As {@link #forEachIndex(int, int, long, long, PausableAction)}, with the arrival spread stated explicitly.
     *
     * @param concurrency         how many actions may run at the same time
     * @param count               the number of indices to cover
     * @param minThinkTimeMillis  lower end of the intended pause
     * @param maxThinkTimeMillis  upper end of the intended pause
     * @param arrivalSpreadMillis window across which the users enter this phase; zero starts them all together
     * @param action              the work to perform for one index
     */
    public static void forEachIndex(
        int concurrency,
        int count,
        long minThinkTimeMillis,
        long maxThinkTimeMillis,
        long arrivalSpreadMillis,
        PausableAction action
    ) {
        Semaphore permits = new Semaphore(concurrency);

        // close() waits for every submitted task to finish, so the method returns only once the whole batch is done.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < count; index++) {
                int currentIndex = index;
                executor.submit(() -> {
                    // Arrive before taking a permit: a student who has not started yet is not one of the students
                    // talking to the server, and holding a slot while waiting would turn the spread into a throughput
                    // limit instead of a pacing device.
                    if (arrivalSpreadMillis > 0) {
                        Thread.sleep(ThreadLocalRandom.current().nextLong(arrivalSpreadMillis + 1));
                    }
                    // Acquired outside the try so that an interrupt while waiting does not release a permit we never
                    // took.
                    permits.acquire();
                    ThinkTime thinkTime = () -> {
                        // Hand the permit back so another user can work while this one waits.
                        permits.release();
                        try {
                            Thread.sleep(nextThinkTimeMillis(minThinkTimeMillis, maxThinkTimeMillis));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            // Uninterruptibly on purpose: the caller's finally releases a permit unconditionally, so
                            // returning from here without one would hand back a permit this user never held and let the
                            // limit drift upwards over a run. The interrupt is preserved above and observed by the
                            // action itself.
                            permits.acquireUninterruptibly();
                        }
                    };
                    // Published on the thread rather than passed through every signature: each user runs on its own
                    // virtual thread for the whole of its action, so the pacing is naturally per-user context, and the
                    // code that makes the requests is several call layers below this one.
                    CURRENT_THINK_TIME.set(thinkTime);
                    try {
                        action.accept(currentIndex, thinkTime);
                    } finally {
                        CURRENT_THINK_TIME.remove();
                        permits.release();
                    }
                    return null;
                });
            }
        }
    }

    /**
     * The pacing of the user running on this thread, if it was started by
     * {@link #forEachIndex(int, int, PausableAction)}.
     */
    private static final ThreadLocal<ThinkTime> CURRENT_THINK_TIME = ThreadLocal.withInitial(() -> ThinkTime.NONE);

    /**
     * @return the pacing for the user running on this thread, or a pause that does nothing outside a simulation
     */
    public static ThinkTime currentThinkTime() {
        return CURRENT_THINK_TIME.get();
    }

    /**
     * @param minThinkTimeMillis lower end of the intended range
     * @param maxThinkTimeMillis upper end of the intended range
     * @return how long a user waits before its next action, drawn log-normally with the range's mean
     */
    static long nextThinkTimeMillis(long minThinkTimeMillis, long maxThinkTimeMillis) {
        // Log-normal rather than uniform, with the same mean. Two reasons, one of them measured.
        //
        // Real reading times are long-tailed: most pauses are short, a few are much longer, and there is no upper
        // bound a student politely respects. A uniform draw has neither property.
        //
        // The measured reason is that a uniform draw barely desynchronises a cohort. Over ten actions the spread of
        // accumulated uniform 5-10 s pauses is only a few seconds, so 2000 students released together stay together:
        // in the 2000-user run against staging1 one second carried exactly 2000 /api/public/time requests, every
        // student having reached the same navigation at the same instant. That spike is an artefact of the draw, not
        // something an exam does. A long tail spreads the cohort out as the phase goes on, at no cost in mean pace.
        double mean = (minThinkTimeMillis + maxThinkTimeMillis) / 2.0;
        // Chosen so a pause is typically a little under the mean and occasionally several times it; sigma near 0.6 is
        // the usual fit for human dwell times.
        double sigma = 0.6;
        double median = mean * Math.exp((-sigma * sigma) / 2);
        double draw = median * Math.exp(ThreadLocalRandom.current().nextGaussian() * sigma);
        // Bounded so neither end can distort a run: nobody reacts instantly, and one unlucky draw must not hold a
        // student past the end of the exam.
        return Math.round(Math.clamp(draw, minThinkTimeMillis / 2.0, maxThinkTimeMillis * 6.0));
    }

    /**
     * An action that runs for one index and can pause between its own steps.
     */
    @FunctionalInterface
    public interface PausableAction {
        /**
         * @param index     the index this action covers
         * @param thinkTime the pause to call between steps
         */
        void accept(int index, ThinkTime thinkTime);
    }
}

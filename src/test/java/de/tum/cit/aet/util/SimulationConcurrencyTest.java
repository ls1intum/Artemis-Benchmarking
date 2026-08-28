package de.tum.cit.aet.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SimulationConcurrencyTest {

    @Test
    void concurrencyForUsesTheStudentCountWhenItFitsUnderTheCeiling() {
        assertThat(SimulationConcurrency.concurrencyFor(150, 200)).isEqualTo(150);
    }

    @Test
    void concurrencyForClampsToTheCeiling() {
        assertThat(SimulationConcurrency.concurrencyFor(5000, 200)).isEqualTo(200);
    }

    @Test
    void concurrencyForNeverReturnsLessThanOne() {
        assertThat(SimulationConcurrency.concurrencyFor(0, 200)).isEqualTo(1);
        assertThat(SimulationConcurrency.concurrencyFor(100, 0)).isEqualTo(1);
    }

    /**
     * A run larger than the ceiling still simulates every student, queueing the excess rather than dropping it.
     */
    @Test
    void forEachIndexStillCoversEveryStudentWhenTheCeilingBinds() {
        java.util.Set<Integer> seen = Collections.synchronizedSet(new HashSet<>());

        SimulationConcurrency.forEachIndex(SimulationConcurrency.concurrencyFor(1000), 1000, seen::add);

        assertThat(seen).hasSize(1000);
    }

    @Test
    void concurrencyForDefaultsToTheDocumentedCeiling() {
        assertThat(SimulationConcurrency.concurrencyFor(Integer.MAX_VALUE)).isEqualTo(SimulationConcurrency.DEFAULT_MAX_CONCURRENCY);
    }

    @Test
    void forEachIndexCoversEveryIndexExactlyOnce() {
        Set<Integer> seen = Collections.synchronizedSet(new HashSet<>());

        SimulationConcurrency.forEachIndex(16, 500, seen::add);

        assertThat(seen)
            .hasSize(500)
            .containsAll(List.of(0, 249, 499));
    }

    @Test
    void forEachIndexReturnsOnlyAfterEveryActionHasFinished() {
        AtomicInteger finished = new AtomicInteger();

        SimulationConcurrency.forEachIndex(8, 200, index -> {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finished.incrementAndGet();
        });

        assertThat(finished).hasValue(200);
    }

    @Test
    void forEachIndexNeverExceedsTheConcurrencyLimit() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger highWaterMark = new AtomicInteger();

        SimulationConcurrency.forEachIndex(5, 200, index -> {
            highWaterMark.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            inFlight.decrementAndGet();
        });

        assertThat(highWaterMark).hasValueLessThanOrEqualTo(5);
    }

    /**
     * The point of the change: a student who is only waiting must not occupy a platform thread, so far more students
     * can be in flight than the host has cores. A fixed pool sized from the core count could not pass this.
     */
    @Test
    void forEachIndexRunsFarMoreBlockedActionsThanTheHostHasCores() throws InterruptedException {
        int blocked = Runtime.getRuntime().availableProcessors() * 100;
        CountDownLatch allArrived = new CountDownLatch(blocked);
        CountDownLatch release = new CountDownLatch(1);

        Thread runner = Thread.ofPlatform().start(() ->
            SimulationConcurrency.forEachIndex(blocked, blocked, index -> {
                allArrived.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            })
        );

        assertThat(allArrived.await(30, TimeUnit.SECONDS)).as("all %d actions should be in flight at once", blocked).isTrue();

        release.countDown();
        runner.join();
    }

    @Test
    void pauseLetsAnotherUserWorkWhileThisOneThinks() {
        // With a single permit the other user can only start if the one that goes first hands its permit back while
        // thinking. Which index wins the permit is up to the scheduler, so the users are named by arrival order.
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger arrivals = new AtomicInteger();

        SimulationConcurrency.forEachIndex(1, 2, 30, 30, (index, thinkTime) -> {
            String user = arrivals.getAndIncrement() == 0 ? "first" : "second";
            events.add("start " + user);
            if ("first".equals(user)) {
                thinkTime.pause();
            }
            events.add("end " + user);
        });

        assertThat(events).containsExactly("start first", "start second", "end second", "end first");
    }

    @Test
    void pausingDoesNotLetMoreUsersWorkAtOnceThanTheLimitAllows() {
        // The pause releases a permit and takes it again. If that accounting drifted, the limit would creep upwards
        // over a run and the tool would generate more load than it was asked for.
        int limit = 3;
        AtomicInteger working = new AtomicInteger();
        AtomicInteger highWaterMark = new AtomicInteger();

        SimulationConcurrency.forEachIndex(limit, 40, 1, 3, (index, thinkTime) -> {
            for (int step = 0; step < 5; step++) {
                highWaterMark.accumulateAndGet(working.incrementAndGet(), Math::max);
                working.decrementAndGet();
                thinkTime.pause();
            }
        });

        assertThat(highWaterMark).hasValueLessThanOrEqualTo(limit);
    }

    @Test
    void pauseWaitsForAboutTheGivenThinkTime() {
        // The draw is log-normal around the range's mean, so a single pause may fall below the nominal minimum; what
        // it may not do is fail to wait at all. The floor is half the minimum, see
        // SimulationConcurrency#nextThinkTimeMillis.
        long start = System.nanoTime();

        SimulationConcurrency.forEachIndex(1, 1, 60, 60, (index, thinkTime) -> thinkTime.pause());

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(30));
    }

    @Test
    void everyUserGetsItsOwnThinkTime() {
        Set<SimulationConcurrency.ThinkTime> seen = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

        SimulationConcurrency.forEachIndex(4, 20, 1, 1, (index, thinkTime) -> seen.add(thinkTime));

        assertThat(seen).hasSize(20);
    }

    @Test
    void theThinkTimeOnTheCurrentThreadIsTheOneHandedToTheAction() {
        // The request code is several call layers below the action, so it reads the pacing off the thread rather than
        // taking it as a parameter. That lookup has to find the same pause the action was given.
        List<Boolean> matches = Collections.synchronizedList(new ArrayList<>());

        SimulationConcurrency.forEachIndex(4, 20, 1, 1, (index, thinkTime) ->
            matches.add(SimulationConcurrency.currentThinkTime() == thinkTime)
        );

        assertThat(matches).hasSize(20).containsOnly(true);
    }

    @Test
    void thinkTimeOutsideASimulationDoesNothing() {
        long start = System.nanoTime();

        SimulationConcurrency.currentThinkTime().pause();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void forEachIndexWithoutAThinkTimeStillCoversEveryIndex() {
        AtomicInteger completed = new AtomicInteger();

        SimulationConcurrency.forEachIndex(4, 50, index -> completed.incrementAndGet());

        assertThat(completed).hasValue(50);
    }

    @Test
    void forEachIndexLetsTheOtherActionsFinishWhenOneThrows() {
        AtomicInteger completed = new AtomicInteger();

        SimulationConcurrency.forEachIndex(4, 50, index -> {
            if (index == 17) {
                throw new IllegalStateException("this student fails");
            }
            completed.incrementAndGet();
        });

        assertThat(completed).hasValue(49);
    }
}

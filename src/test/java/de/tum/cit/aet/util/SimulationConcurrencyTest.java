package de.tum.cit.aet.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashSet;
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

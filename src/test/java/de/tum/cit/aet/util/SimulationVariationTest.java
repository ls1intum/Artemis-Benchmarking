package de.tum.cit.aet.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The cohort must not move as one block.
 * <p>
 * A 2000-student run against staging1 put exactly 2000 {@code /api/public/time} requests into a single second: every
 * student had reached the same navigation at the same instant. That is a property of how the run paces and releases
 * students, not of anything Artemis does, and it is what these tests pin down.
 */
class SimulationVariationTest {

    @Test
    void thinkTimeIsLongTailedRatherThanUniform() {
        List<Long> pauses = drawThinkTimes(4000, 5000, 10_000);

        double mean = pauses.stream().mapToLong(Long::longValue).average().orElseThrow();
        assertTrue(mean > 6000 && mean < 9000, "the mean pause should still be about 7.5 s, was " + Math.round(mean));

        long shortPauses = pauses
            .stream()
            .filter(p -> p < 5000)
            .count();
        long longPauses = pauses
            .stream()
            .filter(p -> p > 15_000)
            .count();
        assertTrue(shortPauses > 0, "a long-tailed draw produces pauses below the nominal minimum");
        assertTrue(longPauses > 0, "a long-tailed draw produces pauses well above the nominal maximum");

        long distinct = pauses.stream().distinct().count();
        assertTrue(distinct > pauses.size() / 2, "pauses should be spread, not clustered on a few values");
    }

    @Test
    void thinkTimeStaysWithinBoundsThatCannotDistortARun() {
        List<Long> pauses = drawThinkTimes(4000, 5000, 10_000);
        assertTrue(pauses.stream().allMatch(p -> p >= 2500), "nobody reacts instantly");
        assertTrue(pauses.stream().allMatch(p -> p <= 60_000), "one unlucky draw must not hold a student past the exam");
    }

    @Test
    void studentsEnterAPhaseSpreadOutRatherThanAllAtOnce() {
        Set<Long> startDeciseconds = new ConcurrentSkipListSet<>();
        long begin = System.nanoTime();

        SimulationConcurrency.forEachIndex(50, 50, 1, 1, 600, (index, thinkTime) ->
            startDeciseconds.add((System.nanoTime() - begin) / 100_000_000L)
        );

        assertTrue(startDeciseconds.size() > 1, "50 students all started within the same tenth of a second");
    }

    @Test
    void aZeroSpreadStillCoversEveryStudent() {
        AtomicInteger covered = new AtomicInteger();
        SimulationConcurrency.forEachIndex(10, 200, 1, 1, 0, (index, thinkTime) -> covered.incrementAndGet());
        assertEquals(200, covered.get());
    }

    private static List<Long> drawThinkTimes(int count, long min, long max) {
        // The draw itself, not a real pause: sleeping four thousand times would take hours and measure the scheduler.
        return java.util.stream.LongStream.range(0, count)
            .mapToObj(draw -> SimulationConcurrency.nextThinkTimeMillis(min, max))
            .toList();
    }
}

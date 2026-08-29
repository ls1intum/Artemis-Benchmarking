package de.tum.cit.aet.service.simulation;

import static de.tum.cit.aet.service.simulation.SimulationExecutionService.bundleDownloadConcurrency;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The bundle phase is not held to the limit that governs the rest of the run.
 * <p>
 * That limit bounds how many students do work the tool host pays for — REST calls, clones, pushes. Downloading the
 * bundle is idle waiting on a socket, and it is the one moment a real exam genuinely happens all at once. Holding it
 * to the same ceiling measured the tool instead of the server: at 200 students times six parallel fetches, a
 * 2000-student run sat at about 1,100 requests in flight and never rose, whatever Artemis did.
 */
class BundleDownloadConcurrencyTest {

    @Test
    void byDefaultTheWholeCohortDownloadsAtOnce() {
        assertThat(bundleDownloadConcurrency(0, 2000)).isEqualTo(2000);
        assertThat(bundleDownloadConcurrency(0, 50)).isEqualTo(50);
    }

    @Test
    void aConfiguredCeilingIsHonoured() {
        assertThat(bundleDownloadConcurrency(400, 2000)).isEqualTo(400);
    }

    @Test
    void theCeilingNeverExceedsTheCohort() {
        assertThat(bundleDownloadConcurrency(5000, 300)).isEqualTo(300);
    }

    @Test
    void thereIsAlwaysSomethingToRun() {
        // The semaphore behind the phase rejects a permit count below one.
        assertThat(bundleDownloadConcurrency(0, 0)).isEqualTo(1);
        assertThat(bundleDownloadConcurrency(-5, 10)).isEqualTo(10);
    }
}

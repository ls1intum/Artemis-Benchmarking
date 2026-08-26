package de.tum.cit.aet.service.artemis.interaction;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Guards the two per-student costs that decide how many students a run can hold.
 * <p>
 * A 2000-student run died with {@code OutOfMemoryError} on both of them at once: every student had its own websocket
 * buffers and its own copy of the JDK trust store. Neither is visible in a small run, so neither is caught by anything
 * other than a test that states the invariant.
 */
class SimulationMemoryAtScaleTest {

    @Test
    void theTlsContextIsBuiltOncePerProcessNotOncePerStudent() {
        var first = SimulatedArtemisUser.sharedSslContext();
        for (int student = 0; student < 100; student++) {
            assertSame(first, SimulatedArtemisUser.sharedSslContext(), "student " + student + " built its own trust store");
        }
    }

    @Test
    void theWebsocketBufferStaysSmallEnoughForACohort() {
        // The container allocates both buffers eagerly, and the text one holds chars, so a student costs
        // 3 x MAX_MESSAGE_BUFFER before a single frame arrives. At the 512 KB this once was, 2000 students needed 3 GB
        // of buffer alone.
        int perStudentBytes = 3 * SimulatedArtemisWebsocket.MAX_MESSAGE_BUFFER;
        int forTwoThousandStudents = perStudentBytes * 2000;
        assertTrue(
            forTwoThousandStudents < 512 * 1024 * 1024,
            "websocket buffers would cost " + forTwoThousandStudents / (1024 * 1024) + " MB for 2000 students"
        );
    }
}
